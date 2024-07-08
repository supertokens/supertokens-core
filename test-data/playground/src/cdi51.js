let allFirstFactors = [
    "emailpassword",
    "otp-phone",
    "otp-email",
    "link-phone",
    "link-email",
    "thirdparty",
];

export const update_v2 = (body, currentState) => {
    let newTenantState = {...currentState, ...get_v2(currentState)};

    if (body.firstFactors === undefined && body.requiredSecondaryFactors === undefined) {
        return currentState; // no updates required
    }

    if (
        currentState.emailPasswordEnabled === false &&
        currentState.passwordlessEnabled === false &&
        currentState.thirdPartyEnabled === false
    ) {
        newTenantState.firstFactors = [];
    }

    newTenantState.emailPasswordEnabled = true;
    newTenantState.passwordlessEnabled = true;
    newTenantState.thirdPartyEnabled = true;

    if (body.firstFactors !== undefined) {
        newTenantState.firstFactors = body.firstFactors;

        if (
            newTenantState.firstFactors === null ||
            newTenantState.firstFactors.includes("emailpassword")
        ) {
            newTenantState.emailPasswordEnabled = true;
        }
        if (
            newTenantState.firstFactors === null ||
            newTenantState.firstFactors.includes("otp-phone") ||
            newTenantState.firstFactors.includes("otp-email") ||
            newTenantState.firstFactors.includes("link-phone") ||
            newTenantState.firstFactors.includes("link-email")
        ) {
            newTenantState.passwordlessEnabled = true;
        }
        if (
            newTenantState.firstFactors === null ||
            newTenantState.firstFactors.includes("thirdparty")
        ) {
            newTenantState.thirdPartyEnabled = true;
        }
    }
    if (body.requiredSecondaryFactors !== undefined) {
        newTenantState.requiredSecondaryFactors = body.requiredSecondaryFactors;

        if (
            newTenantState.requiredSecondaryFactors !== null &&
            newTenantState.requiredSecondaryFactors.includes("emailpassword")
        ) {
            newTenantState.emailPasswordEnabled = true;
        }
        if (
            newTenantState.requiredSecondaryFactors !== null &&
            (newTenantState.requiredSecondaryFactors.includes("otp-phone") ||
                newTenantState.requiredSecondaryFactors.includes("otp-email") ||
                newTenantState.requiredSecondaryFactors.includes("link-phone") ||
                newTenantState.requiredSecondaryFactors.includes("link-email"))
        ) {
            newTenantState.passwordlessEnabled = true;
        }
        if (
            newTenantState.requiredSecondaryFactors !== null &&
            newTenantState.requiredSecondaryFactors.includes("thirdparty")
        ) {
            newTenantState.thirdPartyEnabled = true;
        }
    }

    // if (newTenantState.firstFactors !== null && newTenantState.firstFactors.length === 0) {
    //   if (body.requiredSecondaryFactors === undefined) {
    //     newTenantState.requiredSecondaryFactors = null;
    //   }
    // }

    return newTenantState;
};

export const create_v2 = (body) => {
    let newTenantState;
    let {tenantId} = body;

    if (tenantId === "public") {
        newTenantState = {
            tenantId,
            emailPasswordEnabled: true,
            passwordlessEnabled: true,
            thirdPartyEnabled: true,
            firstFactors: null,
            requiredSecondaryFactors: null,
        };
    } else {
        newTenantState = {
            tenantId,
            emailPasswordEnabled: true,
            passwordlessEnabled: true,
            thirdPartyEnabled: true,
            firstFactors: [],
            requiredSecondaryFactors: null,
        };
    }

    if (body.firstFactors !== undefined) {
        newTenantState.firstFactors = body.firstFactors;
    }
    if (body.requiredSecondaryFactors !== undefined) {
        newTenantState.requiredSecondaryFactors = body.requiredSecondaryFactors;
    }

    return update_v2(body, newTenantState);
};

export const get_v2 = (tenantState) => {
    let firstFactors = tenantState.firstFactors;
    if (firstFactors === null) {
        if (
            tenantState.emailPasswordEnabled === false ||
            tenantState.passwordlessEnabled === false ||
            tenantState.thirdPartyEnabled === false
        ) {
            firstFactors = [...allFirstFactors];

            if (tenantState.emailPasswordEnabled === false) {
                firstFactors = firstFactors.filter(
                    (factor) => factor !== "emailpassword"
                );
            }
            if (tenantState.passwordlessEnabled === false) {
                firstFactors = firstFactors.filter((factor) => factor !== "otp-phone");
                firstFactors = firstFactors.filter((factor) => factor !== "otp-email");
                firstFactors = firstFactors.filter((factor) => factor !== "link-phone");
                firstFactors = firstFactors.filter((factor) => factor !== "link-email");
            }
            if (tenantState.thirdPartyEnabled === false) {
                firstFactors = firstFactors.filter((factor) => factor !== "thirdparty");
            }
        }
    }
    return {
        firstFactors: firstFactors,
        requiredSecondaryFactors: tenantState.requiredSecondaryFactors,
    };
};

export const cdi51CoreBehaviour = (tenantState) => {
    let res = "Core does not block any of the APIs";
    return res;
};

export const backendCdi51Behaviour = (tenantState) => {
    let state = get_v2(tenantState);

    let res = "";

    res += "firstFactors from core: ";
    if (state.firstFactors === null) {
        res += "✗\n";
    } else {
        res += "✓\n";
    }

    res += "enabled booleans in tenant: ✗\n";

    res += "mfa init firstFactors: ";
    if (state.firstFactors !== null) {
        res += "✗\n";
    } else {
        res += "✓\n";
    }

    res += "initialised recipes: ";
    res += "✓\n";

    res += "\n";

    res += "loginMethodsGET output: ";

    let out = {
        emailPasswordEnabled:
            state.firstFactors === null ||
            state.firstFactors.includes("emailpassword"),
        passwordlessEnabled:
            state.firstFactors === null ||
            state.firstFactors.includes("otp-phone") ||
            state.firstFactors.includes("otp-email") ||
            state.firstFactors.includes("link-phone") ||
            state.firstFactors.includes("link-email"),
        thirdPartyEnabled:
            state.firstFactors === null || state.firstFactors.includes("thirdparty"),
    };
    if (state.firstFactors === null) {
        out.firstFactors = [...allFirstFactors];
    } else {
        if (state.firstFactors.length === 0) {
            out.firstFactors = [];
        } else {
            out.firstFactors = state.firstFactors;
        }
    }
    delete out.requiredSecondaryFactors;
    res += JSON.stringify(out, null, 2).replaceAll('\\"', "'") + "\n";

    return res;
};

export const getv2FrontendLoginMethods = (tenantState) => {
    let state = get_v2(tenantState);

    let firstFactors;
    if (state.firstFactors === null) {
        firstFactors = [...allFirstFactors];
    } else {
        firstFactors = [...state.firstFactors];
    }

    return firstFactors.length === 0 ? "none" : firstFactors.join(" ");
};

export const frontendCdi51Behaviour = (tenantState) => {
    let res = "";

    res += "loginMethodsGET: ";
    res += "✓\n";

    res += "initialised recipes: ";
    res += "✓\n";

    res += "mfa init firstFactors: ";
    res += "✗\n";

    res += "\n";

    res += "ui shows: " + getv2FrontendLoginMethods(tenantState) + "\n";

    return res;
};
