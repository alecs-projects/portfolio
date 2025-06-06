package name.abuchen.portfolio.datatransfer.pdf;

import static name.abuchen.portfolio.datatransfer.ExtractorUtils.checkAndSetTax;
import static name.abuchen.portfolio.util.TextUtil.trim;

import java.math.BigDecimal;
import java.text.MessageFormat;
import java.util.Map;

import name.abuchen.portfolio.Messages;
import name.abuchen.portfolio.datatransfer.ExtractorUtils;
import name.abuchen.portfolio.datatransfer.pdf.PDFParser.Block;
import name.abuchen.portfolio.datatransfer.pdf.PDFParser.DocumentType;
import name.abuchen.portfolio.datatransfer.pdf.PDFParser.Transaction;
import name.abuchen.portfolio.model.AccountTransaction;
import name.abuchen.portfolio.model.BuySellEntry;
import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.model.PortfolioTransaction;
import name.abuchen.portfolio.money.CurrencyUnit;
import name.abuchen.portfolio.money.Money;
import name.abuchen.portfolio.money.Values;

/**
 * @formatter:off
 * @implNote Score Priority is a US-based financial services company.
 *           The currency is US$.
 *
 * @implSpec All security currencies are USD.
 *           The CUSIP number is the WKN number.
 *           The amount of dividends is reported in gross.
 * @formatter:on
 */

@SuppressWarnings("nls")
public class ScorePriorityIncPDFExtractor extends AbstractPDFExtractor
{
    public ScorePriorityIncPDFExtractor(Client client)
    {
        super(client);

        addBankIdentifier("Score Priority");

        addAccountStatementTransaction();
    }

    @Override
    public String getLabel()
    {
        return "Score Priority Corp. / Just2Trade US";
    }

    private void addAccountStatementTransaction()
    {
        final DocumentType type = new DocumentType("ACCOUNT STATEMENT", //
                        documentContext -> documentContext //
                                        // @formatter:off
                                        // John Doe STATEMENT PERIOD: September 1 - 30, 2021
                                        // @formatter:on
                                        .section("year") //
                                        .match("^.* STATEMENT PERIOD: .*, (?<year>[\\d]{4})$") //
                                        .assign((ctx, v) -> ctx.put("year", v.get("year"))));

        this.addDocumentTyp(type);

        // @formatter:off
        // Formatting:
        // Date | Effective Description | CUSIP | Type of Activity | Quantity Market Price | Net Settlement Amount
        // -------------------------------------
        // Sep 15 Vanguard Index Fds 922908363 Buy 4 409.61 (1,638.44)
        // S P 500 Etf Shs
        // Sep 02 Netflix Inc 64110L106 Sell 2 566.20 1,132.39
        // Com
        // @formatter:on
        Block blockBuySell = new Block("^[\\w]{3} [\\d]{2} .* [\\w]{9} (Buy|Sell) [\\.,\\d]+ [\\.,\\d]+ (\\()?[\\.,\\d]+(\\)?)$");
        type.addBlock(blockBuySell);
        blockBuySell.set(new Transaction<BuySellEntry>()

                        .subject(() -> {
                            BuySellEntry portfolioTransaction = new BuySellEntry();
                            portfolioTransaction.setType(PortfolioTransaction.Type.BUY);
                            return portfolioTransaction;
                        })

                        .section("month", "day", "name", "wkn", "type", "shares", "amount", "nameContinued") //
                        .documentContext("year") //
                        .match("^(?<month>[\\w]{3}) (?<day>[\\d]{2}) (?<name>.*) (?<wkn>[\\w]{9}) (?<type>(Buy|Sell)) (?<shares>[\\.,\\d]+) [\\.,\\d]+ (\\()?(?<amount>[\\.,\\d]+)(\\))?$") //
                        .match("(?<nameContinued>.*)") //
                        .assign((t, v) -> {
                            // Is type --> "Sell" change from BUY to SELL
                            if ("Sell".equals(v.get("type")))
                                t.setType(PortfolioTransaction.Type.SELL);

                            Map<String, String> context = type.getCurrentContext();
                            v.put("date", v.get("day") + " " + v.get("month") + " " + context.get("year"));
                            v.put("currency", CurrencyUnit.USD);

                            t.setDate(asDate(v.get("date")));
                            t.setShares(asShares(v.get("shares")));
                            t.setAmount(asAmount(v.get("amount")));
                            t.setCurrencyCode(asCurrencyCode(v.get("currency")));
                            t.setSecurity(getOrCreateSecurity(v));
                        })

                        .wrap(BuySellEntryItem::new));

        // @formatter:off
        // Formatting:
        // Date | Effective Description | CUSIP | Type of Activity | Quantity Market Price | Net Settlement Amount
        // -------------------------------------
        // Sep 16 Barrick Gold Co             14 067901108 Dividend 1.97
        //
        // Sep 17 Barrick Gold Co             14 067901108 Dividend 1.26
        // Sep 17 For Sec Withhold: Div   .25000 067901108 Foreign Withholding (0.31)
        //
        // Sep 15 Realty Income C             22 756109104 Dividend 5.18
        //  Sep 15 Nra Withhold: Dividend 756109104 NRA Withhold (1.55)
        //
        // Sep 15 Tyson Foods Inc              6 902494103 Qualified Dividend 2.67
        // Sep 15 Nra Withhold: Dividend 902494103 NRA Withhold (0.80)
        // @formatter:on
        Block blockDividende = new Block("^[\\w]{3} [\\d]{2} .* (?!Qualified).{9} (Qualified )?Dividend [\\.,\\d]+$");
        type.addBlock(blockDividende);
        blockDividende.set(new Transaction<AccountTransaction>()

                        .subject(() -> {
                            AccountTransaction accountTransaction = new AccountTransaction();
                            accountTransaction.setType(AccountTransaction.Type.DIVIDENDS);
                            return accountTransaction;
                        })

                        .oneOf( //
                                        section -> section //
                                                        .attributes("month", "day", "name", "shares", "wkn", "amount", "tax") //
                                                        .documentContext("year") //
                                                        .match("^(?<month>[\\w]{3}) (?<day>[\\d]{2}) (?<name>.*) (?<shares>[\\.,\\d]+) (?<wkn>(?!Qualified).{9}) (Qualified )?Dividend (?<amount>[\\.,\\d]+)$") //
                                                        .match("^[\\w]{3} [\\d]{2} .* [\\w]{9} (NRA Withhold|Foreign Withholding) \\((?<tax>[\\.,\\d]+)\\)$") //
                                                        .assign((t, v) -> {
                                                            v.put("date", v.get("day") + " " + v.get("month") + " " + v.get("year"));
                                                            v.put("currency", CurrencyUnit.USD);

                                                            t.setDateTime(asDate(v.get("date")));
                                                            t.setShares(asShares(v.get("shares")));
                                                            t.setAmount(asAmount(v.get("amount")) - asAmount(v.get("tax")));
                                                            t.setCurrencyCode(asCurrencyCode(v.get("currency")));
                                                            t.setSecurity(getOrCreateSecurity(v));

                                                            Money tax = Money.of(asCurrencyCode(v.get("currency")), asAmount(v.get("tax")));

                                                            checkAndSetTax(tax, t, type.getCurrentContext());
                                                        }),
                                        section -> section //
                                                        .attributes("month", "day", "name", "shares", "wkn", "amount") //
                                                        .documentContext("year") //
                                                        .match("^(?<month>.*) (?<day>[\\d]{2}) (?<name>.*) (?<shares>[\\.,\\d]+) (?<wkn>(?!Qualified).{9}) (Qualified )?Dividend (?<amount>[\\.,\\d]+)$") //
                                                        .assign((t, v) -> {
                                                            v.put("date", v.get("day") + " " + v.get("month") + " " + v.get("year"));
                                                            v.put("currency", CurrencyUnit.USD);

                                                            t.setDateTime(asDate(v.get("date")));
                                                            t.setShares(asShares(v.get("shares")));
                                                            t.setAmount(asAmount(v.get("amount")));
                                                            t.setCurrencyCode(asCurrencyCode(v.get("currency")));
                                                            t.setSecurity(getOrCreateSecurity(v));
                                                        }))

                        .wrap(TransactionItem::new));

        // @formatter:off
        // Formatting:
        // Date | Effective Description | CUSIP | Type of Activity | Quantity Market Price | Net Settlement Amount
        // -------------------------------------
        // Nov 05 2seventy Bio Inc 901384107 Security Journal 5
        // Common Stock
        // Nov 15 Orion Office Reit Inc 68629Y103 Security Journal 2
        // Com
        // @formatter:on
        Block blockDeliveryInBound = new Block("^[\\w]{3} [\\d]{2} .* [\\w]{9} Security Journal [\\.,\\d]+$");
        type.addBlock(blockDeliveryInBound);
        blockDeliveryInBound.set(new Transaction<PortfolioTransaction>()

                        .subject(() -> {
                            PortfolioTransaction portfolioTransaction = new PortfolioTransaction();
                            portfolioTransaction.setType(PortfolioTransaction.Type.DELIVERY_INBOUND);
                            return portfolioTransaction;
                        })

                        .section("month", "day", "name", "wkn", "shares", "nameContinued") //
                        .documentContext("year") //
                        .match("^(?<month>[\\w]{3}) (?<day>[\\d]{2}) (?<name>.*) (?<wkn>[\\w]{9}) Security Journal (?<shares>[\\.,\\d]+)$") //
                        .match("(?<nameContinued>.*)") //
                        .assign((t, v) -> {
                            v.put("date", v.get("day") + " " + v.get("month") + " " + v.get("year"));
                            v.put("currency", CurrencyUnit.USD);

                            t.setDateTime(asDate(v.get("date")));
                            t.setShares(asShares(v.get("shares")));
                            t.setAmount(0L);
                            t.setCurrencyCode(asCurrencyCode(v.get("currency")));
                            t.setSecurity(getOrCreateSecurity(v));
                        })

                        .wrap(TransactionItem::new));

        // @formatter:off
        // Formatting:
        // Date | Effective Description | CUSIP | Type of Activity | Quantity Market Price | Net Settlement Amount
        // -------------------------------------
        // Nov 05 Ca Fee_spinoff_blue Tsvt 09609 Journal (30.00) <-- CUSIP is incorrect (length = 9)
        // Nov 15 Ca Fee_spinoff_o Onl 756109104 Journal (30.00)
        // @formatter:on
        Block blockFees = new Block("^[\\w]{3} [\\d]{2} Ca Fee_spinoff.* Journal \\([\\.,\\d]+\\)$");
        type.addBlock(blockFees);
        blockFees.set(new Transaction<AccountTransaction>()

                        .subject(() -> {
                            AccountTransaction accountTransaction = new AccountTransaction();
                            accountTransaction.setType(AccountTransaction.Type.FEES);
                            return accountTransaction;
                        })

                        .section("month", "day", "name", "wkn", "amount") //
                        .documentContext("year") //
                        .match("^(?<month>[\\w]{3}) (?<day>[\\d]{2}) Ca Fee_spinoff.* (?<name>.*) (?<wkn>.*) Journal \\((?<amount>[\\.,\\d]+)\\)$") //
                        .assign((t, v) -> {
                            v.put("date", v.get("day") + " " + v.get("month") + " " + v.get("year"));
                            v.put("currency", CurrencyUnit.USD);

                            // if CUSIP lenght != 9
                            if (trim(v.get("wkn")).length() < 9)
                                v.getTransactionContext().put(FAILURE, MessageFormat.format(Messages.MsgErrorInvalidWKN, trim(v.get("name"))));
                            else
                                t.setSecurity(getOrCreateSecurity(v));

                            t.setDateTime(asDate(v.get("date")));
                            t.setAmount(asAmount(v.get("amount")));
                            t.setCurrencyCode(asCurrencyCode(v.get("currency")));
                        })

                        .wrap((t, ctx) -> {
                            TransactionItem item = new TransactionItem(t);

                            if (ctx.getString(FAILURE) != null)
                                item.setFailureMessage(ctx.getString(FAILURE));

                            return item;
                        }));

        // @formatter:off
        // Formatting:
        // Date | Effective Description | CUSIP | Type of Activity | Quantity Market Price | Net Settlement Amount
        // -------------------------------------
        // Jun 23 Cil Allocation 58933Y105 Journal 29.98
        //  Merck & Co Inc New
        // @formatter:on
        Block blockCashAllocation = new Block("^[\\w]{3} [\\d]{2} .* Allocation [\\w]{9} Journal [\\.,\\d]+$");
        type.addBlock(blockCashAllocation);
        blockCashAllocation.set(new Transaction<AccountTransaction>()

                        .subject(() -> {
                            AccountTransaction accountTransaction = new AccountTransaction();
                            accountTransaction.setType(AccountTransaction.Type.DIVIDENDS);
                            return accountTransaction;
                        })

                        .section("month", "day", "name", "wkn", "amount") //
                        .documentContext("year") //
                        .match("^(?<month>[\\w]{3}) (?<day>[\\d]{2}) .* Allocation (?<wkn>[\\w]{9}) Journal (?<amount>[\\.,\\d]+)$") //
                        .match("^(?<name>.*)$") //
                        .assign((t, v) -> {
                            v.put("date", v.get("day") + " " + v.get("month") + " " + v.get("year"));
                            v.put("currency", CurrencyUnit.USD);

                            t.setDateTime(asDate(v.get("date")));
                            t.setShares(0L);
                            t.setAmount(asAmount(v.get("amount")));
                            t.setCurrencyCode(asCurrencyCode(v.get("currency")));
                            t.setSecurity(getOrCreateSecurity(v));
                        })

                        .wrap(TransactionItem::new));

        // @formatter:off
        // Formatting:
        // Date | Effective Description | CUSIP | Type of Activity | Quantity Market Price | Net Settlement Amount
        // -------------------------------------
        // Dec 29 Incoming Wire Abccdd Doe Journal 71,000.00
        // @formatter:on
        Block blockDeposit = new Block("^[\\w]{3} [\\d]{2} Incoming Wire .* [\\.,\\d]+$");
        type.addBlock(blockDeposit);
        blockDeposit.set(new Transaction<AccountTransaction>()

                        .subject(() -> {
                            AccountTransaction accountTransaction = new AccountTransaction();
                            accountTransaction.setType(AccountTransaction.Type.DEPOSIT);
                            return accountTransaction;
                        })

                        .section("month", "day", "amount") //
                        .documentContext("year") //
                        .match("^(?<month>[\\w]{3}) (?<day>[\\d]{2}) Incoming Wire .* (?<amount>[\\.,\\d]+)$") //
                        .assign((t, v) -> {
                            v.put("date", v.get("day") + " " + v.get("month") + " " + v.get("year"));
                            v.put("currency", CurrencyUnit.USD);

                            t.setDateTime(asDate(v.get("date")));
                            t.setAmount(asAmount(v.get("amount")));
                            t.setCurrencyCode(asCurrencyCode(v.get("currency")));
                        })

                        .wrap(TransactionItem::new));

        // @formatter:off
        // Formatting:
        // Date | Effective Description | CUSIP | Type of Activity | Quantity Market Price | Net Settlement Amount
        // -------------------------------------
        // Dec 31 .05000% 3 Days,Bal=   $71000 Credit Interest 0.30
        // @formatter:on
        Block blockInterest = new Block("^[\\w]{3} [\\d]{2} .* Credit Interest [\\.,\\d]+$");
        type.addBlock(blockInterest);
        blockInterest.set(new Transaction<AccountTransaction>()

                        .subject(() -> {
                            AccountTransaction accountTransaction = new AccountTransaction();
                            accountTransaction.setType(AccountTransaction.Type.INTEREST);
                            return accountTransaction;
                        })

                        .section("month", "day", "amount") //
                        .documentContext("year") //
                        .match("^(?<month>[\\w]{3}) (?<day>[\\d]{2}) .* Credit Interest (?<amount>[\\.,\\d]+)$") //
                        .assign((t, v) -> {
                            v.put("date", v.get("day") + " " + v.get("month") + " " + v.get("year"));
                            v.put("currency", CurrencyUnit.USD);

                            t.setDateTime(asDate(v.get("date")));
                            t.setAmount(asAmount(v.get("amount")));
                            t.setCurrencyCode(asCurrencyCode(v.get("currency")));
                        })

                        .wrap(TransactionItem::new));
    }

    @Override
    protected long asAmount(String value)
    {
        return ExtractorUtils.convertToNumberLong(value, Values.Amount, "en", "US");
    }

    @Override
    protected long asShares(String value)
    {
        return ExtractorUtils.convertToNumberLong(value, Values.Share, "en", "US");
    }

    @Override
    protected BigDecimal asExchangeRate(String value)
    {
        return ExtractorUtils.convertToNumberBigDecimal(value, Values.Share, "en", "US");
    }
}
