package name.abuchen.portfolio.online;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import name.abuchen.portfolio.PortfolioLog;
import name.abuchen.portfolio.model.Exchange;
import name.abuchen.portfolio.model.LatestSecurityPrice;
import name.abuchen.portfolio.model.Security;
import name.abuchen.portfolio.model.SecurityPrice;

public interface QuoteFeed // NOSONAR
{
    String MANUAL = "MANUAL"; //$NON-NLS-1$

    /**
     * Returns the technical identifier of the quote feed.
     */
    String getId();

    /**
     * Returns the display name of the quote feed.
     */
    String getName();

    /**
     * Returns true if the download request should be merged, i.e. first the
     * historical prices and then immediately the latest prices. It is used if
     * the quote provider does not support different APIs for historical and
     * latest prices and the underlying request can be cached.
     */
    default boolean mergeDownloadRequests()
    {
        return false;
    }

    default int getMaxRateLimitAttempts()
    {
        return 3;
    }

    /**
     * Returns the help URL to be shown to the user.
     */
    default Optional<String> getHelpURL()
    {
        return Optional.empty();
    }

    /**
     * Update the latest quote of the given securities.
     */
    default Optional<LatestSecurityPrice> getLatestQuote(Security security) throws QuoteFeedException
    {
        QuoteFeedData data = getHistoricalQuotes(security, false);

        if (!data.getErrors().isEmpty())
            PortfolioLog.error(data.getErrors());

        List<LatestSecurityPrice> prices = data.getLatestPrices();
        if (prices.isEmpty())
            return Optional.empty();

        Collections.sort(prices, new SecurityPrice.ByDate());

        return Optional.of(prices.get(prices.size() - 1));
    }

    /**
     * Retrieves the historical quotes of the given security. The quote provider
     * may reduce the response to only include newly updated quotes.
     */
    QuoteFeedData getHistoricalQuotes(Security security, boolean collectRawResponse) throws QuoteFeedException;

    /**
     * Retrieves a sample of historical quotes of the given security. The list
     * of quotes may be reduced to the last 2 months or latest 100 entries.
     */
    default QuoteFeedData previewHistoricalQuotes(Security security) throws QuoteFeedException
    {
        return getHistoricalQuotes(security, true);
    }

    default List<Exchange> getExchanges(Security subject, List<Exception> errors)
    {
        return Collections.emptyList();
    }
}
