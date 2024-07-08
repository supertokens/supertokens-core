// randomly pick a version to create tenant
// randomly pick body for creation
// apply 10 updates randomly, each time choosing different version and body
// verify ui state is same across all versions at any point in time
// repeat everything 2 lakh times

import {create_4, getCdi4FrontendLoginMethods, get_4, update_4} from "./cdi4";
import {create_5, getCdi5FrontendLoginMethods, get_5, update_5} from "./cdi5";
import * as deepcopy from 'deepcopy';

import {
    create_v2,
    get_v2,
    getv2FrontendLoginMethods,
    update_v2,
} from "./cdi51";

const testCases = {
    4: [
        [{}, {emailPasswordEnabled: true}, {emailPasswordEnabled: false}],
        [{}, {passwordlessEnabled: true}, {passwordlessEnabled: false}],
    ],
    5: [
        [{}, {emailPasswordEnabled: true}, {emailPasswordEnabled: false}],
        [{}, {passwordlessEnabled: true}, {passwordlessEnabled: false}],
        [
            {},
            {firstFactors: null},
            {firstFactors: ["emailpassword"]},
            {firstFactors: ["otp-phone"]},
            {firstFactors: ["emailpassword", "otp-phone"]},
            {firstFactors: ["emailpassword", "custom"]},
        ],
        [
            {},
            {requiredSecondaryFactors: null},
            {requiredSecondaryFactors: ["emailpassword"]},
            {requiredSecondaryFactors: ["otp-phone"]},
            {requiredSecondaryFactors: ["emailpassword", "otp-phone"]},
            {requiredSecondaryFactors: ["emailpassword", "custom"]},
        ],
    ],
    v2: [
        [
            {},
            {firstFactors: null},
            {firstFactors: []},
            {firstFactors: ["emailpassword"]},
            {firstFactors: ["otp-phone"]},
            {firstFactors: ["emailpassword", "otp-phone"]},
            {firstFactors: ["emailpassword", "custom"]},
        ],
        [
            {},
            {requiredSecondaryFactors: null},
            {requiredSecondaryFactors: ["emailpassword"]},
            {requiredSecondaryFactors: ["otp-phone"]},
            {requiredSecondaryFactors: ["emailpassword", "otp-phone"]},
            {requiredSecondaryFactors: ["emailpassword", "custom"]},
        ],
    ],
};

function generateCombinations(arrayOfArrays) {
    const results = [];

    function combine(current, index) {
        if (index === arrayOfArrays.length) {
            results.push(Object.assign({}, ...current));
            return;
        }

        for (let obj of arrayOfArrays[index]) {
            combine([...current, obj], index + 1);
        }
    }

    combine([], 0);
    return results;
}

function checkIfInvalid(body) {
    if (body.emailPasswordEnabled === false) {
        if (body.firstFactors !== undefined && body.firstFactors !== null && body.firstFactors.includes("emailpassword")) {
            return true;
        }
        if (body.requiredSecondaryFactors !== undefined && body.requiredSecondaryFactors !== null && body.requiredSecondaryFactors.includes("emailpassword")) {
            return true;
        }
    }

    if (body.passwordlessEnabled === false) {
        if (body.firstFactors !== undefined && body.firstFactors !== null && (body.firstFactors.includes("otp-phone") || body.firstFactors.includes("otp-email") || body.firstFactors.includes("link-phone") || body.firstFactors.includes("link-email"))) {
            return true;
        }
        if (body.requiredSecondaryFactors !== undefined && body.requiredSecondaryFactors !== null && (body.requiredSecondaryFactors.includes("otp-phone") || body.requiredSecondaryFactors.includes("otp-email") || body.requiredSecondaryFactors.includes("link-phone") || body.requiredSecondaryFactors.includes("link-email"))) {
            return true;
        }
    }

    if (body.thirdPartyEnabled === false) {
        if (body.firstFactors !== undefined && body.firstFactors !== null && body.firstFactors.includes("thirdparty")) {
            return true;
        }
        if (body.requiredSecondaryFactors !== undefined && body.requiredSecondaryFactors !== null && body.requiredSecondaryFactors.includes("thirdparty")) {
            return true;
        }
    }

    // if (body.firstFactors !== undefined && body.firstFactors !== null && body.firstFactors.length === 0) {
    //   if (body.requiredSecondaryFactors !== undefined && body.requiredSecondaryFactors !== null) {
    //     return true;
    //   }
    // }

    return false;
}

function doTest() {
    let versions = ["4", "5", "v2"];
    let tenantIds = ["public", "t1"];
    let versionCombinations = {};
    for (let v of versions) {
        versionCombinations[v] = generateCombinations(testCases[v]);
    }

    let cases = [];

    for (const tenantId of tenantIds) {
        for (const cv of versions) {
            for (const uv of versions) {
                const cbodies = versionCombinations[cv];
                const ubodies = versionCombinations[uv];
                for (const cbody of cbodies) {
                    for (const ubody of ubodies) {
                        let tenantState;
                        if (cv === "4") {
                            tenantState = create_4({...deepcopy(cbody), tenantId});
                        } else if (cv === "5") {
                            tenantState = create_5({...deepcopy(cbody), tenantId});
                        } else if (cv === "v2") {
                            tenantState = create_v2({...deepcopy(cbody), tenantId});
                        }

                        if (uv === "4") {
                            tenantState = update_4({...deepcopy(ubody)}, tenantState);
                        } else if (uv === "5") {
                            tenantState = update_5({...deepcopy(ubody)}, tenantState);
                        } else if (uv === "v2") {
                            tenantState = update_v2({...deepcopy(ubody)}, tenantState);
                        }

                        let invalid = false;
                        // if (tenantState.firstFactors !== null && tenantState.firstFactors.length === 0) {
                        //   if (tenantState.requiredSecondaryFactors !== null) {
                        //     invalid = true;
                        //   }
                        // }

                        cases.push({
                            tenantId,
                            cv,
                            uv,
                            cbody,
                            ubody,
                            tenantState,
                            g4: get_4(tenantState),
                            g5: get_5(tenantState),
                            gv2: get_v2(tenantState),
                            invalidConfig: invalid || checkIfInvalid(cbody) || checkIfInvalid(ubody),
                        });

                        let ui4 = getCdi4FrontendLoginMethods(tenantState)
                            .trim()
                            .split(" ")
                            .sort()
                            .join(" ");
                        let ui5 = getCdi5FrontendLoginMethods(tenantState)
                            .trim()
                            .split(" ")
                            .sort()
                            .join(" ");
                        let uiv2 = getv2FrontendLoginMethods(tenantState)
                            .trim()
                            .split(" ")
                            .sort()
                            .join(" ");

                        if (ui4 !== ui5 || ui5 !== uiv2 || ui4 !== uiv2) {
                            if (ui5 === uiv2) {
                                let ok = true
                                for (const f of ui5.split(' ')) {
                                    if (f === 'custom') {
                                        continue;
                                    }
                                    if (!ui4.includes(f)) {
                                        ok = false
                                    }
                                }
                                if (ok) {
                                    continue;
                                }
                            }
                            console.log("Mismatch found");
                            console.log("UI in CDI 4", ui4);
                            console.log("UI in CDI 5", ui5);
                            console.log("UI in CDI v2", uiv2);
                            throw new Error("Mismatch found");
                        }
                    }
                }
            }
        }
    }

    console.log(cases);
    downloadArrayAsJsonLines(cases, "cdi-tests");
}

function downloadArrayAsJsonLines(exportArray, exportName) {
    // Convert each object to JSON and join with newlines
    const jsonLines = exportArray.map((obj) => JSON.stringify(obj)).join("\n");

    // Create a Blob with the JSON lines
    const blob = new Blob([jsonLines], {type: "application/x-jsonlines"});

    // Create a download link
    const url = URL.createObjectURL(blob);
    const downloadLink = document.createElement("a");
    downloadLink.href = url;
    downloadLink.download = exportName + ".jsonl";

    // Append to the document, trigger click, and remove
    document.body.appendChild(downloadLink);
    downloadLink.click();
    document.body.removeChild(downloadLink);

    // Clean up the URL object
    URL.revokeObjectURL(url);
}

export function runTests() {
    doTest();
}
