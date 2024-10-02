// randomly pick a version to create tenant
// randomly pick body for creation
// apply 10 updates randomly, each time choosing different version and body
// verify ui state is same across all versions at any point in time
// repeat everything 2 lakh times

import {create_4, getCdi4FrontendLoginMethods, update_4} from "./cdi4";
import {create_5, getCdi5FrontendLoginMethods, update_5} from "./cdi5";
import {create_v2, getv2FrontendLoginMethods, update_v2} from "./cdi51";

function getRandomElement(arr) {
    if (arr.length === 0) {
        return undefined; // Handle empty array
    }
    const randomIndex = Math.floor(Math.random() * arr.length);
    return arr[randomIndex];
}

function createRandomTenant() {
    let v = getRandomElement(["4", "5", "v2"]);
    let b = {};

    b.tenantId = getRandomElement(["public", "t1"]);

    let tenantState;
    if (v === "4") {
        b.emailPasswordEnabled = getRandomElement([undefined, true, false]);
        b.passwordlessEnabled = getRandomElement([undefined, true, false]);
        b.thirdPartyEnabled = getRandomElement([undefined, true, false]);
        tenantState = create_4(b);
    } else if (v === "5") {
        b.emailPasswordEnabled = getRandomElement([undefined, true, false]);
        b.passwordlessEnabled = getRandomElement([undefined, true, false]);
        b.thirdPartyEnabled = getRandomElement([undefined, true, false]);
        b.firstFactors = getRandomElement([undefined, null, ["emailpassword"]]);
        b.requiredSecondaryFactors = getRandomElement([
            undefined,
            null,
            ["otp-phone"],
        ]);
        tenantState = create_5(b);
    } else if (v === "v2") {
        b.firstFactors = getRandomElement([undefined, null, [], ["emailpassword"]]);
        b.requiredSecondaryFactors = getRandomElement([
            undefined,
            null,
            ["otp-phone"],
        ]);
        tenantState = create_v2(b);
    }

    return {
        tenantState,
        step: {
            v,
            b,
        },
    };
}

function applyRandomUpdate(tenantState) {
    let v = getRandomElement(["4", "5", "v2"]);
    let b = {};

    b.tenantId = getRandomElement(["public", "t1"]);

    if (v === "4") {
        b.emailPasswordEnabled = getRandomElement([undefined, true, false]);
        b.passwordlessEnabled = getRandomElement([undefined, true, false]);
        b.thirdPartyEnabled = getRandomElement([undefined, true, false]);
        tenantState = update_4(b, tenantState);
    } else if (v === "5") {
        b.emailPasswordEnabled = getRandomElement([undefined, true, false]);
        b.passwordlessEnabled = getRandomElement([undefined, true, false]);
        b.thirdPartyEnabled = getRandomElement([undefined, true, false]);
        b.firstFactors = getRandomElement([undefined, null, ["emailpassword"]]);
        b.requiredSecondaryFactors = getRandomElement([
            undefined,
            null,
            ["otp-phone"],
        ]);
        tenantState = update_5(b, tenantState);
    } else if (v === "v2") {
        b.firstFactors = getRandomElement([undefined, null, [], ["emailpassword"]]);
        b.requiredSecondaryFactors = getRandomElement([
            undefined,
            null,
            ["otp-phone"],
        ]);
        tenantState = update_v2(b, tenantState);
    }

    return {
        tenantState,
        step: {
            v,
            b,
        },
    };
}

function doTest() {
    let steps = [];
    let currentState;
    {
        let {tenantState, step} = createRandomTenant();
        steps.push(step);
        currentState = tenantState;
    }

    for (let uId = 0; uId < 10; uId++) {
        let {tenantState, step} = applyRandomUpdate(currentState);
        steps.push(step);
        currentState = tenantState;

        let ui4 = getCdi4FrontendLoginMethods(currentState)
            .trim()
            .split(" ")
            .sort()
            .join(" ");
        let ui5 = getCdi5FrontendLoginMethods(currentState)
            .trim()
            .split(" ")
            .sort()
            .join(" ");
        let uiv2 = getv2FrontendLoginMethods(currentState)
            .trim()
            .split(" ")
            .sort()
            .join(" ");

        if (ui4 !== ui5 || ui5 !== uiv2 || ui4 !== uiv2) {
            console.log("Mismatch found");
            console.log("UI in CDI 4", ui4);
            console.log("UI in CDI 5", ui5);
            console.log("UI in CDI v2", uiv2);
            console.log("State", currentState);
            console.log("Steps", steps);
            throw new Error("Mismatch found");
        }
    }
}

export function runTests() {
    for (let tId = 0; tId < 2000000; tId++) {
        doTest();

        if (tId % 1000 === 999) {
            console.log(tId + 1, "tests done");
        }
    }
    console.log("All tests passed!!!!")
}
