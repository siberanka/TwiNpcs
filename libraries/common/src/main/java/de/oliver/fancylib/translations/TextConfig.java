package de.oliver.fancylib.translations;

public class TextConfig {

    private final String primaryColor;
    private final String secondaryColor;
    private final String successColor;
    private final String warningColor;
    private final String errorColor;
    private final String prefix;
    private volatile boolean prefixEnabled = true;

    public TextConfig(
            String primaryColor,
            String secondaryColor,
            String successColor,
            String warningColor,
            String errorColor,
            String prefix
    ) {
        this.primaryColor = primaryColor;
        this.secondaryColor = secondaryColor;
        this.successColor = successColor;
        this.warningColor = warningColor;
        this.errorColor = errorColor;
        this.prefix = prefix;
    }

    public String primaryColor() {
        return primaryColor;
    }

    public String secondaryColor() {
        return secondaryColor;
    }

    public String successColor() {
        return successColor;
    }

    public String warningColor() {
        return warningColor;
    }

    public String errorColor() {
        return errorColor;
    }

    public String prefix() {
        return prefixEnabled ? prefix : "";
    }

    public void setPrefixEnabled(boolean prefixEnabled) {
        this.prefixEnabled = prefixEnabled;
    }
}
