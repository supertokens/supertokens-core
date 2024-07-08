let allFirstFactors = [
    "emailpassword",
    "otp-phone",
    "otp-email",
    "link-phone",
    "link-email",
    "thirdparty",
];

export const update_5 = (body, currentState) => {
    debugger;
    let newTenantState = {...currentState};
    if (Object.keys(body).length === 0) {
        return newTenantState;
    }

    let firstFactors = currentState.firstFactors;
    let requiredSecondaryFactors = currentState.requiredSecondaryFactors;

    if (
        body.emailPasswordEnabled === true ||
        body.passwordlessEnabled === true ||
        body.thirdPartyEnabled === true
    ) {
        if (firstFactors !== null && firstFactors.length === 0) {
            firstFactors = null;
            newTenantState.emailPasswordEnabled = false;
            newTenantState.passwordlessEnabled = false;
            newTenantState.thirdPartyEnabled = false;
        }
    }

    if (
        firstFactors === null &&
        body.requiredSecondaryFactors !== undefined &&
        body.requiredSecondaryFactors !== null
    ) {
        const updFirstFactors = () => {
            firstFactors = [...allFirstFactors];
            if ((currentState.emailPasswordEnabled === false && body.emailPasswordEnabled !== true) || body.emailPasswordEnabled === false) {
                firstFactors = firstFactors.filter(
                    (factor) => factor !== "emailpassword"
                );
            }
            if ((currentState.passwordlessEnabled === false && body.passwordlessEnabled !== true) || body.passwordlessEnabled === false) {
                firstFactors = firstFactors.filter((factor) => factor !== "otp-phone");
                firstFactors = firstFactors.filter((factor) => factor !== "otp-email");
                firstFactors = firstFactors.filter((factor) => factor !== "link-phone");
                firstFactors = firstFactors.filter((factor) => factor !== "link-email");
            }
            if ((currentState.thirdPartyEnabled === false && body.thirdPartyEnabled !== true) || body.thirdPartyEnabled === false) {
                firstFactors = firstFactors.filter((factor) => factor !== "thirdparty");
            }

            if (firstFactors.length === 6) {
                firstFactors = null;
            }
        };

        currentState = get_5(currentState);

        if (
            currentState.emailPasswordEnabled === false &&
            body.requiredSecondaryFactors.includes("emailpassword") && body.emailPasswordEnabled !== true
        ) {
            updFirstFactors();
        }

        if (
            currentState.passwordlessEnabled === false &&
            (body.requiredSecondaryFactors.includes("otp-phone") ||
                body.requiredSecondaryFactors.includes("otp-email") ||
                body.requiredSecondaryFactors.includes("link-phone") ||
                body.requiredSecondaryFactors.includes("link-email")) &&
            body.passwordlessEnabled !== true
        ) {
            updFirstFactors();
        }

        if (
            currentState.thirdPartyEnabled === false &&
            body.requiredSecondaryFactors.includes("thirdparty") && body.thirdPartyEnabled !== true
        ) {
            updFirstFactors();
        }
    }

    if (body.emailPasswordEnabled === true) {
        newTenantState.emailPasswordEnabled = true;
    }
    if (body.passwordlessEnabled === true) {
        newTenantState.passwordlessEnabled = true;
    }
    if (body.thirdPartyEnabled === true) {
        newTenantState.thirdPartyEnabled = true;
    }

    if (body.firstFactors !== undefined) {
        firstFactors = body.firstFactors;
        if (firstFactors !== null) {
            if (firstFactors.includes("emailpassword")) {
                newTenantState.emailPasswordEnabled = true;
            }
            if (
                firstFactors.includes("otp-phone") ||
                firstFactors.includes("otp-email") ||
                firstFactors.includes("link-phone") ||
                firstFactors.includes("link-email")
            ) {
                newTenantState.passwordlessEnabled = true;
            }
            if (firstFactors.includes("thirdparty")) {
                newTenantState.thirdPartyEnabled = true;
            }
        }
    }

    if (body.requiredSecondaryFactors !== undefined) {
        requiredSecondaryFactors = body.requiredSecondaryFactors;
        if (requiredSecondaryFactors !== null) {
            if (requiredSecondaryFactors.includes("emailpassword")) {
                newTenantState.emailPasswordEnabled = true;
            }
            if (
                requiredSecondaryFactors.includes("otp-phone") ||
                requiredSecondaryFactors.includes("otp-email") ||
                requiredSecondaryFactors.includes("link-phone") ||
                requiredSecondaryFactors.includes("link-email")
            ) {
                newTenantState.passwordlessEnabled = true;
            }
            if (requiredSecondaryFactors.includes("thirdparty")) {
                newTenantState.thirdPartyEnabled = true;
            }
        }
    }

    if (body.emailPasswordEnabled === false) {
        if (firstFactors !== null) {
            firstFactors = firstFactors.filter(
                (factor) => factor !== "emailpassword"
            );
        }

        if (requiredSecondaryFactors !== null) {
            requiredSecondaryFactors = requiredSecondaryFactors.filter(
                (factor) => factor !== "emailpassword"
            );
        }

        newTenantState.emailPasswordEnabled = false;
    }
    if (body.passwordlessEnabled === false) {
        if (firstFactors !== null) {
            firstFactors = firstFactors.filter((factor) => factor !== "otp-phone");
            firstFactors = firstFactors.filter((factor) => factor !== "otp-email");
            firstFactors = firstFactors.filter((factor) => factor !== "link-phone");
            firstFactors = firstFactors.filter((factor) => factor !== "link-email");
        }

        if (requiredSecondaryFactors !== null) {
            requiredSecondaryFactors = requiredSecondaryFactors.filter(
                (factor) => factor !== "otp-phone"
            );
            requiredSecondaryFactors = requiredSecondaryFactors.filter(
                (factor) => factor !== "otp-email"
            );
            requiredSecondaryFactors = requiredSecondaryFactors.filter(
                (factor) => factor !== "link-phone"
            );
            requiredSecondaryFactors = requiredSecondaryFactors.filter(
                (factor) => factor !== "link-email"
            );
        }

        newTenantState.passwordlessEnabled = false;
    }
    if (body.thirdPartyEnabled === false) {
        if (firstFactors !== null) {
            firstFactors = firstFactors.filter((factor) => factor !== "thirdparty");
        }

        if (requiredSecondaryFactors !== null) {
            requiredSecondaryFactors = requiredSecondaryFactors.filter(
                (factor) => factor !== "thirdparty"
            );
        }
        newTenantState.thirdPartyEnabled = false;
    }

    if (
        requiredSecondaryFactors !== null &&
        requiredSecondaryFactors.length === 0
    ) {
        requiredSecondaryFactors = null;
    }

    // if (firstFactors !== null && firstFactors.length === 0) {
    //   if (body.requiredSecondaryFactors === undefined) {
    //     requiredSecondaryFactors = null;
    //   }
    // }

    // if (newTenantState.emailPasswordEnabled === false && newTenantState.passwordlessEnabled === false && newTenantState.thirdPartyEnabled === false) {
    //   if (body.requiredSecondaryFactors === undefined) {
    //     requiredSecondaryFactors = null;
    //   }
    // }

    newTenantState.firstFactors = firstFactors;
    newTenantState.requiredSecondaryFactors = requiredSecondaryFactors;

    if (newTenantState.firstFactors !== null) {
        for (const factor of newTenantState.firstFactors) {
            if (factor === "emailpassword") {
                newTenantState.emailPasswordEnabled = true;
            }
            if (
                factor === "otp-phone" ||
                factor === "otp-email" ||
                factor === "link-phone" ||
                factor === "link-email"
            ) {
                newTenantState.passwordlessEnabled = true;
            }
            if (factor === "thirdparty") {
                newTenantState.thirdPartyEnabled = true;
            }
        }
    }
    if (newTenantState.requiredSecondaryFactors !== null) {
        for (const factor of newTenantState.requiredSecondaryFactors) {
            if (factor === "emailpassword") {
                newTenantState.emailPasswordEnabled = true;
            }
            if (
                factor === "otp-phone" ||
                factor === "otp-email" ||
                factor === "link-phone" ||
                factor === "link-email"
            ) {
                newTenantState.passwordlessEnabled = true;
            }
            if (factor === "thirdparty") {
                newTenantState.thirdPartyEnabled = true;
            }
        }
    }

    return newTenantState;
};

export const create_5 = (body) => {
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
            emailPasswordEnabled: false,
            passwordlessEnabled: false,
            thirdPartyEnabled: false,
            firstFactors: null,
            requiredSecondaryFactors: null,
        };
    }

    const {emailPasswordEnabled, passwordlessEnabled, thirdPartyEnabled} = body;
    newTenantState = update_5(
        {emailPasswordEnabled, passwordlessEnabled, thirdPartyEnabled},
        newTenantState
    );
    const {firstFactors, requiredSecondaryFactors} = body;
    newTenantState = update_5(
        {firstFactors, requiredSecondaryFactors},
        newTenantState
    );

    return newTenantState;
};

export const get_5 = (tenantState) => {
    return {
        emailPasswordEnabled:
            tenantState.emailPasswordEnabled &&
            (tenantState.firstFactors === null ||
                tenantState.firstFactors.length > 0),
        thirdPartyEnabled:
            tenantState.thirdPartyEnabled &&
            (tenantState.firstFactors === null ||
                tenantState.firstFactors.length > 0),
        passwordlessEnabled:
            tenantState.passwordlessEnabled &&
            (tenantState.firstFactors === null ||
                tenantState.firstFactors.length > 0),
        firstFactors:
            tenantState.firstFactors !== null && tenantState.firstFactors.length === 0
                ? null
                : tenantState.firstFactors,
        requiredSecondaryFactors: tenantState.requiredSecondaryFactors,
    };
};

export const cdi5CoreBehaviour = (tenantState) => {
    let state = get_5(tenantState);
    let res = "";

    if (state.emailPasswordEnabled === false) {
        res += "emailpassword APIs are blocked\n";
    } else {
        res += "emailpassword APIs are allowed\n";
    }

    if (state.passwordlessEnabled === false) {
        res += "passwordless APIs are blocked\n";
    } else {
        res += "passwordless APIs are allowed\n";
    }

    if (state.thirdPartyEnabled === false) {
        res += "thirdParty APIs are blocked\n";
    } else {
        res += "thirdParty APIs are allowed\n";
    }

    return res;
};

export const backendCdi5Behaviour = (tenantState) => {
    let state = get_5(tenantState);

    let res = "";

    res += "firstFactors from core: ";
    if (state.firstFactors === null) {
        res += "✗\n";
    } else {
        res += "✓\n";
    }

    res += "enabled booleans in tenant: ✓\n";

    res += "mfa init firstFactors: ";
    if (state.firstFactors !== null) {
        res += "✗\n";
    } else {
        res += "✓\n";
    }

    res += "initialised recipes: ";
    res += "✓\n";

    res += "\nloginMethodsGET output: ";

    let out = {...state};
    if (state.firstFactors === null) {
        let firstFactors = [...allFirstFactors];
        if (state.emailPasswordEnabled === false) {
            firstFactors = firstFactors.filter(
                (factor) => factor !== "emailpassword"
            );
        }
        if (state.passwordlessEnabled === false) {
            firstFactors = firstFactors.filter((factor) => factor !== "otp-phone");
            firstFactors = firstFactors.filter((factor) => factor !== "otp-email");
            firstFactors = firstFactors.filter((factor) => factor !== "link-phone");
            firstFactors = firstFactors.filter((factor) => factor !== "link-email");
        }
        if (state.thirdPartyEnabled === false) {
            firstFactors = firstFactors.filter((factor) => factor !== "thirdparty");
        }
        out.firstFactors = firstFactors;
    } else {
        let firstFactors = [...state.firstFactors];
        if (state.emailPasswordEnabled === false) {
            firstFactors = firstFactors.filter(
                (factor) => factor !== "emailpassword"
            );
        }
        if (state.passwordlessEnabled === false) {
            firstFactors = firstFactors.filter((factor) => factor !== "otp-phone");
            firstFactors = firstFactors.filter((factor) => factor !== "otp-email");
            firstFactors = firstFactors.filter((factor) => factor !== "link-phone");
            firstFactors = firstFactors.filter((factor) => factor !== "link-email");
        }
        if (state.thirdPartyEnabled === false) {
            firstFactors = firstFactors.filter((factor) => factor !== "thirdparty");
        }
        out.firstFactors = firstFactors;
    }
    delete out.requiredSecondaryFactors;
    res += JSON.stringify(out, null, 2).replaceAll('\\"', "'") + "\n";

    return res;
};

export const getCdi5FrontendLoginMethods = (tenantState) => {
    let state = get_5(tenantState);

    let firstFactors;
    if (state.firstFactors === null) {
        firstFactors = [...allFirstFactors];
        if (state.emailPasswordEnabled === false) {
            firstFactors = firstFactors.filter(
                (factor) => factor !== "emailpassword"
            );
        }
        if (state.passwordlessEnabled === false) {
            firstFactors = firstFactors.filter((factor) => factor !== "otp-phone");
            firstFactors = firstFactors.filter((factor) => factor !== "otp-email");
            firstFactors = firstFactors.filter((factor) => factor !== "link-phone");
            firstFactors = firstFactors.filter((factor) => factor !== "link-email");
        }
        if (state.thirdPartyEnabled === false) {
            firstFactors = firstFactors.filter((factor) => factor !== "thirdparty");
        }
    } else {
        firstFactors = [...state.firstFactors];
        if (state.emailPasswordEnabled === false) {
            firstFactors = firstFactors.filter(
                (factor) => factor !== "emailpassword"
            );
        }
        if (state.passwordlessEnabled === false) {
            firstFactors = firstFactors.filter((factor) => factor !== "otp-phone");
            firstFactors = firstFactors.filter((factor) => factor !== "otp-email");
            firstFactors = firstFactors.filter((factor) => factor !== "link-phone");
            firstFactors = firstFactors.filter((factor) => factor !== "link-email");
        }
        if (state.thirdPartyEnabled === false) {
            firstFactors = firstFactors.filter((factor) => factor !== "thirdparty");
        }
    }

    return firstFactors.length === 0 ? "none" : firstFactors.join(" ");
};

export const frontendCdi5Behaviour = (tenantState) => {
    let res = "";

    res += "loginMethodsGET: ";
    res += "✓\n";

    res += "initialised recipes: ";
    res += "✓\n";

    res += "mfa init firstFactors: ";
    res += "✗\n";

    res += "\n";

    res += "ui shows: " + getCdi5FrontendLoginMethods(tenantState) + "\n";

    return res;
};
