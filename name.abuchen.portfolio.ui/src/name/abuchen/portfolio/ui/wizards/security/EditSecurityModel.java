package name.abuchen.portfolio.ui.wizards.security;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.base.Strings;

import name.abuchen.portfolio.model.AttributeType;
import name.abuchen.portfolio.model.Attributes;
import name.abuchen.portfolio.model.Classification;
import name.abuchen.portfolio.model.Classification.Assignment;
import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.model.Security;
import name.abuchen.portfolio.model.SecurityProperty;
import name.abuchen.portfolio.model.Taxonomy;
import name.abuchen.portfolio.model.Taxonomy.Visitor;
import name.abuchen.portfolio.ui.util.BindingHelper;

/* package */final class EditSecurityModel extends BindingHelper.Model
{
    public enum SortOrder
    {
        /** by natural order, i.e., the order defined by the taxonomy */
        NATURAL,
        /** by name */
        NAME,
        /** by weight */
        WEIGHT
    }

    static class TaxonomyDesignation extends BindingHelper.Model
    {
        private Taxonomy taxonomy;
        private Security security;
        private List<Classification> elements;

        private List<ClassificationLink> links = new ArrayList<>();

        private SortOrder sortOrder = SortOrder.NATURAL;

        public TaxonomyDesignation(Taxonomy taxonomy, final Security security)
        {
            this.taxonomy = taxonomy;
            this.security = security;
            this.elements = taxonomy.getRoot().getTreeElements();

            taxonomy.foreach(new Visitor()
            {
                @Override
                public void visit(Classification classification, Assignment assignment)
                {
                    if (assignment.getInvestmentVehicle().equals(security))
                        links.add(new ClassificationLink(classification, assignment));
                }
            });
        }

        public Taxonomy getTaxonomy()
        {
            return taxonomy;
        }

        public List<Classification> getElements()
        {
            return elements;
        }

        public List<ClassificationLink> getLinks()
        {
            return links;
        }

        public SortOrder getSortOrder()
        {
            return sortOrder;
        }

        public void setSortOrder(SortOrder sortOrder)
        {
            this.sortOrder = sortOrder;
        }

        @Override
        public void applyChanges()
        {
            final Map<Classification, ClassificationLink> classification2link = new HashMap<>();
            for (ClassificationLink link : links)
                classification2link.put(link.getClassification(), link);

            taxonomy.foreach(new Visitor()
            {
                @Override
                public void visit(Classification classification, Assignment assignment)
                {
                    if (assignment.getInvestmentVehicle().equals(security))
                    {
                        ClassificationLink link = classification2link.remove(classification);

                        if (link == null)
                            classification.getAssignments().remove(assignment);
                        else
                            assignment.setWeight(link.getWeight());
                    }
                }
            });

            for (ClassificationLink link : classification2link.values())
            {
                Assignment assignment = new Assignment(security);
                assignment.setWeight(link.getWeight());
                assignment.setRank(Integer.MAX_VALUE);
                link.getClassification().addAssignment(assignment);
            }
        }
    }

    static class ClassificationLink extends BindingHelper.Model
    {
        private Classification classification;
        private int weight;

        public ClassificationLink(Classification classification, Assignment assignment)
        {
            this.classification = classification;
            this.weight = assignment.getWeight();
        }

        public ClassificationLink()
        {
            weight = Classification.ONE_HUNDRED_PERCENT;
        }

        public Classification getClassification()
        {
            return classification;
        }

        public void setClassification(Classification classification)
        {
            this.classification = classification;
        }

        public int getWeight()
        {
            return weight;
        }

        public void setWeight(int weight)
        {
            this.weight = weight;
        }

        @Override
        public void applyChanges()
        {
            // done by TaxonomyDesignation
        }
    }

    static class AttributeDesignation extends BindingHelper.Model
    {
        private final AttributeType type;
        private Object value;

        public AttributeDesignation(AttributeType type, Object value)
        {
            this.type = type;
            this.value = value;
        }

        public AttributeType getType()
        {
            return type;
        }

        public Object getValue()
        {
            return value;
        }

        public void setValue(Object value)
        {
            firePropertyChange("value", this.value, this.value = value); //$NON-NLS-1$ //NOSONAR
        }

        @Override
        public void applyChanges()
        {
            // done by EditSecuritModel
        }
    }

    private Security security;

    private String name;
    private String onlineId;

    private String currencyCode;
    private String targetCurrencyCode;
    private String note;
    private String isin;
    private String tickerSymbol;
    private String wkn;
    private String calendar;
    private String feed;
    private String feedURL;
    private String latestFeed;
    private String latestFeedURL;
    private boolean isRetired;

    /**
     * Used to pipe the status of a manually validated quote provider into the
     * binding context
     */
    private String statusHistoricalQuotesProvider;
    private String statusLatestQuotesProvider;

    private List<TaxonomyDesignation> taxonomies = new ArrayList<>();
    private List<AttributeDesignation> attributes = new ArrayList<>();

    private Map<String, String> feedProperties = new HashMap<>();

    public EditSecurityModel(Client client, Security security)
    {
        super(client);

        this.security = security;

        this.name = security.getName();
        this.onlineId = security.getOnlineId();
        this.currencyCode = security.getCurrencyCode();
        this.targetCurrencyCode = security.getTargetCurrencyCode();
        this.note = security.getNote();
        this.isin = security.getIsin();
        this.tickerSymbol = security.getTickerSymbol();
        this.wkn = security.getWkn();
        this.calendar = security.getCalendar();
        this.feed = security.getFeed();
        this.feedURL = security.getFeedURL();
        this.latestFeed = security.getLatestFeed();
        this.latestFeedURL = security.getLatestFeedURL();
        this.isRetired = security.isRetired();

        for (Taxonomy taxonomy : client.getTaxonomies())
            this.taxonomies.add(new TaxonomyDesignation(taxonomy, security));

        Attributes securityAttributes = security.getAttributes();
        client.getSettings() //
                        .getAttributeTypes() //
                        .filter(securityAttributes::exists) //
                        .filter(a -> a.supports(Security.class)) //
                        .forEach(a -> {
                            AttributeDesignation designation = new AttributeDesignation(a, securityAttributes.get(a));
                            attributes.add(designation);
                        });

        security.getProperties().filter(p -> p.getType() == SecurityProperty.Type.FEED)
                        .forEach(p -> feedProperties.put(p.getName(), p.getValue()));
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        firePropertyChange("name", this.name, this.name = name); //$NON-NLS-1$ //NOSONAR
    }

    public String getOnlineId()
    {
        return onlineId;
    }

    public void setOnlineId(String onlineId)
    {
        firePropertyChange("onlineId", this.onlineId, this.onlineId = onlineId); //$NON-NLS-1$ //NOSONAR
    }

    public String getCurrencyCode()
    {
        return currencyCode;
    }

    public void setCurrencyCode(String currencyCode)
    {
        firePropertyChange("currencyCode", this.currencyCode, this.currencyCode = currencyCode); //$NON-NLS-1$ //NOSONAR
    }

    public String getTargetCurrencyCode()
    {
        return targetCurrencyCode;
    }

    public void setTargetCurrencyCode(String targetCurrencyCode)
    {
        firePropertyChange("targetCurrencyCode", this.targetCurrencyCode, this.targetCurrencyCode = targetCurrencyCode); //$NON-NLS-1$ //NOSONAR
    }

    public String getNote()
    {
        return note;
    }

    public void setNote(String note)
    {
        firePropertyChange("note", this.note, this.note = note); //$NON-NLS-1$ //NOSONAR
    }

    public String getIsin()
    {
        return isin;
    }

    public void setIsin(String isin)
    {
        firePropertyChange("isin", this.isin, this.isin = isin); //$NON-NLS-1$ //NOSONAR
    }

    public String getTickerSymbol()
    {
        return tickerSymbol;
    }

    public void setTickerSymbol(String tickerSymbol)
    {
        firePropertyChange("tickerSymbol", this.tickerSymbol, this.tickerSymbol = tickerSymbol); //$NON-NLS-1$ //NOSONAR
    }

    public String getWkn()
    {
        return wkn;
    }

    public void setWkn(String wkn)
    {
        firePropertyChange("wkn", this.wkn, this.wkn = wkn); //$NON-NLS-1$ //NOSONAR
    }

    public String getCalendar()
    {
        return calendar;
    }

    public void setCalendar(String calendar)
    {
        firePropertyChange("calendar", this.calendar, this.calendar = calendar); //$NON-NLS-1$ //NOSONAR
    }

    public String getFeed()
    {
        return feed;
    }

    public void setFeed(String feed)
    {
        firePropertyChange("feed", this.feed, this.feed = feed); //$NON-NLS-1$ //NOSONAR
    }

    public String getFeedURL()
    {
        return feedURL;
    }

    public void setFeedURL(String feedURL)
    {
        firePropertyChange("feedURL", this.feedURL, this.feedURL = feedURL); //$NON-NLS-1$ //NOSONAR
    }

    public String getLatestFeed()
    {
        return latestFeed;
    }

    public void setLatestFeed(String latestFeed)
    {
        firePropertyChange("latestFeed", this.latestFeed, this.latestFeed = latestFeed); //$NON-NLS-1$ //NOSONAR
    }

    public String getLatestFeedURL()
    {
        return latestFeedURL;
    }

    public void setLatestFeedURL(String latestFeedURL)
    {
        firePropertyChange("latestFeedURL", this.latestFeedURL, this.latestFeedURL = latestFeedURL); //$NON-NLS-1$ //NOSONAR
    }

    public boolean isRetired()
    {
        return isRetired;
    }

    public void setRetired(boolean isRetired)
    {
        firePropertyChange("retired", this.isRetired, this.isRetired = isRetired); //$NON-NLS-1$ //NOSONAR
    }

    public String getStatusHistoricalQuotesProvider()
    {
        return statusHistoricalQuotesProvider;
    }

    public void setStatusHistoricalQuotesProvider(String status)
    {
        firePropertyChange("statusHistoricalQuotesProvider", this.statusHistoricalQuotesProvider, //$NON-NLS-1$
                        this.statusHistoricalQuotesProvider = status); // NOSONAR
    }

    public String getStatusLatestQuotesProvider()
    {
        return statusLatestQuotesProvider;
    }

    public void setStatusLatestQuotesProvider(String status)
    {
        firePropertyChange("statusLatestQuotesProvider", this.statusLatestQuotesProvider, //$NON-NLS-1$
                        this.statusLatestQuotesProvider = status); // NOSONAR
    }

    public List<TaxonomyDesignation> getDesignations()
    {
        return taxonomies;
    }

    public List<AttributeDesignation> getAttributes()
    {
        return attributes;
    }

    public String getFeedProperty(String name)
    {
        return feedProperties.get(name);
    }

    public void setFeedProperty(String name, String value)
    {
        feedProperties.put(name, value);
    }

    public Security getSecurity()
    {
        return security;
    }

    @Override
    public void applyChanges()
    {
        // set all attributes to the current security
        setAttributes(security);
        for (TaxonomyDesignation designation : taxonomies)
            designation.applyChanges();
    }

    /**
     * Sets all currently edited attributes to the given {@link Security}. This
     * function can also be used to create a temporary copy of a security.
     * 
     * @param security
     *            {@link Security}
     */
    public void setAttributes(Security security)
    {
        security.setName(name);
        security.setOnlineId(onlineId);
        security.setCurrencyCode(currencyCode);
        security.setTargetCurrencyCode(targetCurrencyCode);
        security.setNote(note);
        security.setIsin(isin);
        security.setTickerSymbol(tickerSymbol);
        security.setWkn(wkn);
        security.setCalendar(calendar);
        security.setFeed(feed);
        security.setFeedURL(feedURL);
        security.setLatestFeed(latestFeed);
        security.setLatestFeedURL(latestFeedURL);
        security.setRetired(isRetired);

        Attributes a = new Attributes();
        for (AttributeDesignation attribute : attributes)
            a.put(attribute.getType(), attribute.getValue());
        security.setAttributes(a);

        security.removePropertyIf(p -> p.getType() == SecurityProperty.Type.FEED);

        feedProperties.entrySet().stream().filter(entry -> !Strings.isNullOrEmpty(entry.getValue()))
                        .forEach(entry -> security.addProperty(new SecurityProperty(SecurityProperty.Type.FEED,
                                        entry.getKey(), entry.getValue())));
    }
}
