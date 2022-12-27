package io.supertokens.ee;

public enum EE_FEATURES {
    ACCOUNT_LINKING("account_linking"), MULTI_TENANCY("multi_tenancy"), TEST("test");

    private final String name;

    EE_FEATURES(String s) {
        name = s;
    }

    @Override
    public String toString() {
        return this.name;
    }

    public static EE_FEATURES getEnumFromString(String s) {
        for (EE_FEATURES b : EE_FEATURES.values()) {
            if (b.toString().equalsIgnoreCase(s)) {
                return b;
            }
        }
        return null;
    }
}
