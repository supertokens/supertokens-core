import React, {useState} from 'react';
import './index.css';

export default function App(props) {

    const [tenantState, setTenantState] = useState({
        emailPasswordEnabled: null,
        passwordlessEnabled: null,
        thirdPartyEnabled: null,
        firstFactors: null,
        requiredSecondaryFactors: null,
    });

    const [errorMsg, setErrorMsg] = useState("");

    const [cdi4body, setCdi4Body] = useState('{\n  "tenantId": "public"\n}')
    const [cdi5body, setCdi5Body] = useState('{\n  "tenantId": "public"\n}')
    const [v2body, setV2Body] = useState('{\n  "tenantId": "public"\n}')

    const create_4 = (body) => {
        let {tenantId} = body;
        let newTenantState;
        if (tenantId === "public") {
            newTenantState = {
                emailPasswordEnabled: true,
                passwordlessEnabled: true,
                thirdPartyEnabled: true,
                firstFactors: null,
                requiredSecondaryFactors: null,
            }
        } else {
            newTenantState = {
                emailPasswordEnabled: false,
                passwordlessEnabled: false,
                thirdPartyEnabled: false,
                firstFactors: [],
                requiredSecondaryFactors: null,
            }
        }

        if (body.emailPasswordEnabled !== undefined) {
            newTenantState.emailPasswordEnabled = body.emailPasswordEnabled;
        }
        if (body.passwordlessEnabled !== undefined) {
            newTenantState.passwordlessEnabled = body.passwordlessEnabled;
        }
        if (body.thirdPartyEnabled !== undefined) {
            newTenantState.thirdPartyEnabled = body.thirdPartyEnabled;
        }

        if (newTenantState.emailPasswordEnabled === true && newTenantState.passwordlessEnabled === true && newTenantState.thirdPartyEnabled === true) {
            newTenantState.firstFactors = null;
        } else {
            newTenantState.firstFactors = [];
            if (newTenantState.emailPasswordEnabled === true) {
                newTenantState.firstFactors.push("emailpassword");
            }
            if (newTenantState.passwordlessEnabled === true) {
                newTenantState.firstFactors.push("otp-phone");
                newTenantState.firstFactors.push("otp-email");
                newTenantState.firstFactors.push("link-phone");
                newTenantState.firstFactors.push("link-email");
            }
            if (newTenantState.thirdPartyEnabled === true) {
                newTenantState.firstFactors.push("thirdparty");
            }
        }

        setTenantState(newTenantState)
    }

    const update_4 = (body) => {
        let newTenantState = {...tenantState}

        let firstFactors = ["emailpassword", "otp-phone", "otp-email", "link-phone", "link-email", "thirdparty"];

        if (body.emailPasswordEnabled === true) {
            newTenantState.emailPasswordEnabled = true;
            if (newTenantState.firstFactors !== null) {
                if (!newTenantState.firstFactors.includes("emailpassword")) {
                    newTenantState.firstFactors.push("emailpassword");
                }
            }
        }
        if (body.passwordlessEnabled === true) {
            newTenantState.passwordlessEnabled = true;
            if (newTenantState.firstFactors !== null) {
                if (!newTenantState.firstFactors.includes("otp-phone") && !newTenantState.firstFactors.includes("otp-email") && !newTenantState.firstFactors.includes("link-phone") && !newTenantState.firstFactors.includes("link-email")) {
                    newTenantState.firstFactors.push("otp-phone");
                    newTenantState.firstFactors.push("otp-email");
                    newTenantState.firstFactors.push("link-phone");
                    newTenantState.firstFactors.push("link-email");
                }
            }
        }
        if (body.thirdPartyEnabled === true) {
            newTenantState.thirdPartyEnabled = true;
        }

        if (body.emailPasswordEnabled === false || newTenantState.emailPasswordEnabled === false) {
            firstFactors = firstFactors.filter(factor => factor !== "emailpassword");
        }
        if (body.passwordlessEnabled === false || newTenantState.passwordlessEnabled === false) {
            firstFactors = firstFactors.filter(factor => factor !== "otp-phone" && factor !== "otp-email" && factor !== "link-phone" && factor !== "link-email");
        }
        if (body.thirdPartyEnabled === false || newTenantState.thirdPartyEnabled === false) {
            firstFactors = firstFactors.filter(factor => factor !== "thirdparty");
        }

        if (newTenantState.firstFactors === null) {
            if (newTenantState.emailPasswordEnabled === false || newTenantState.passwordlessEnabled === false || newTenantState.thirdPartyEnabled === false) {
                newTenantState.firstFactors = firstFactors;
            } else {
                newTenantState.firstFactors = null;
            }
        } else {
            if (newTenantState.emailPasswordEnabled === false || newTenantState.passwordlessEnabled === false || newTenantState.thirdPartyEnabled === false) {
                newTenantState.firstFactors = firstFactors.filter(factor => newTenantState.firstFactors.includes(factor));
            } else {
                newTenantState.firstFactors = null;
            }
        }

        setTenantState(newTenantState)
    }

    const create_5 = (body) => {
        let newTenantState;
        let {tenantId} = body;

        if (tenantId === "public") {
            newTenantState = {
                emailPasswordEnabled: true,
                passwordlessEnabled: true,
                thirdPartyEnabled: true,
                firstFactors: null,
                requiredSecondaryFactors: null,
            }
        } else {
            newTenantState = {
                emailPasswordEnabled: false,
                passwordlessEnabled: false,
                thirdPartyEnabled: false,
                firstFactors: null,
                requiredSecondaryFactors: null,
            }
        }

        if (body.emailPasswordEnabled !== undefined) {
            newTenantState.emailPasswordEnabled = body.emailPasswordEnabled;
        }
        if (body.passwordlessEnabled !== undefined) {
            newTenantState.passwordlessEnabled = body.passwordlessEnabled;
        }
        if (body.thirdPartyEnabled !== undefined) {
            newTenantState.thirdPartyEnabled = body.thirdPartyEnabled;
        }
        if (body.firstFactors !== undefined) {
            newTenantState.firstFactors = body.firstFactors;
        }
        if (body.requiredSecondaryFactors !== undefined) {
            newTenantState.requiredSecondaryFactors = body.requiredSecondaryFactors;
        }

        setTenantState(newTenantState)
    }

    const update_5 = (body) => {
    }

    const create_v2 = (body) => {
        let newTenantState;
        let {tenantId} = body;

        if (tenantId === "public") {
            newTenantState = {
                emailPasswordEnabled: null,
                passwordlessEnabled: null,
                thirdPartyEnabled: null,
                firstFactors: null,
                requiredSecondaryFactors: null,
            }
        } else {
            newTenantState = {
                emailPasswordEnabled: null,
                passwordlessEnabled: null,
                thirdPartyEnabled: null,
                firstFactors: [],
                requiredSecondaryFactors: null,
            }
        }

        if (body.firstFactors !== undefined) {
            newTenantState.firstFactors = body.firstFactors;
        }
        if (body.requiredSecondaryFactors !== undefined) {
            newTenantState.requiredSecondaryFactors = body.requiredSecondaryFactors;
        }

        setTenantState(newTenantState)
    }

    const update_v2 = (body) => {
    }

    const get_4 = () => {
        return {
            emailPasswordEnabled:
                    tenantState.emailPasswordEnabled !== false &&
                    (
                            (tenantState.firstFactors === null) ||
                            (tenantState.firstFactors !== null && tenantState.firstFactors.includes("emailpassword"))
                    ),
            thirdPartyEnabled:
                    tenantState.thirdPartyEnabled !== false &&
                    (
                            (tenantState.firstFactors === null) ||
                            (tenantState.firstFactors !== null && tenantState.firstFactors.includes("thirdparty"))
                    ),
            passwordlessEnabled:
                    tenantState.passwordlessEnabled !== false &&
                    (
                            (tenantState.firstFactors === null) ||
                            (tenantState.firstFactors !== null && (
                                    tenantState.firstFactors.includes("otp-phone") ||
                                    tenantState.firstFactors.includes("otp-email") ||
                                    tenantState.firstFactors.includes("link-phone") ||
                                    tenantState.firstFactors.includes("link-email")
                            ))
                    ),
        };
    }

    const get_5 = () => {
        return {
            emailPasswordEnabled:
                    tenantState.emailPasswordEnabled === true ||
                    (tenantState.emailPasswordEnabled === null && tenantState.firstFactors === null) ||
                    (tenantState.firstFactors !== null && tenantState.firstFactors.includes("emailpassword")) ||
                    (tenantState.requiredSecondaryFactors !== null && tenantState.requiredSecondaryFactors.includes("emailpassword")),
            thirdPartyEnabled:
                    tenantState.thirdPartyEnabled === true ||
                    (tenantState.thirdPartyEnabled === null && tenantState.firstFactors === null) ||
                    (tenantState.firstFactors !== null && tenantState.firstFactors.includes("thirdparty")) ||
                    (tenantState.requiredSecondaryFactors !== null && tenantState.requiredSecondaryFactors.includes("thirdparty")),
            passwordlessEnabled:
                    tenantState.passwordlessEnabled === true ||
                    (tenantState.passwordlessEnabled === null && tenantState.firstFactors === null) ||
                    (tenantState.firstFactors !== null && (
                            tenantState.firstFactors.includes("otp-phone") ||
                            tenantState.firstFactors.includes("otp-email") ||
                            tenantState.firstFactors.includes("link-phone") ||
                            tenantState.firstFactors.includes("link-email")
                    )) ||
                    (tenantState.requiredSecondaryFactors !== null && (
                            tenantState.requiredSecondaryFactors.includes("otp-phone") ||
                            tenantState.requiredSecondaryFactors.includes("otp-email") ||
                            tenantState.requiredSecondaryFactors.includes("link-phone") ||
                            tenantState.requiredSecondaryFactors.includes("link-email")
                    )),
            firstFactors: tenantState.firstFactors !== null && tenantState.firstFactors.length === 0 ? null : tenantState.firstFactors,
            requiredSecondaryFactors: tenantState.requiredSecondaryFactors,
        };
    }

    const get_v2 = () => {
        return {
            firstFactors: tenantState.firstFactors,
            requiredSecondaryFactors: tenantState.requiredSecondaryFactors,
        };
    }

    return (
            <div className='App'>
                <h3>DB State</h3>
                <pre>{JSON.stringify(tenantState, null, 2)}</pre>

                <div className="row">
                    <div>
                        <h3>Get in 4.0</h3>
                        <pre>{JSON.stringify(get_4(), null, 2)}</pre>
                    </div>
                    <div>
                        <h3>Get in 5.0</h3>
                        <pre>{JSON.stringify(get_5(), null, 2)}</pre>
                    </div>
                    <div>
                        <h3>Get in v2</h3>
                        <pre>{JSON.stringify(get_v2(), null, 2)}</pre>
                    </div>
                </div>

                <div className="row">
                    <div>
                        <textarea value={cdi4body} onChange={(e) => {
                            setCdi4Body(e.target.value)
                        }}></textarea>
                        <button onClick={() => {
                            create_4(JSON.parse(cdi4body))
                        }}>Create
                        </button>
                        <button onClick={() => {
                            update_4(JSON.parse(cdi4body))
                        }}>Update
                        </button>
                    </div>
                    <div>
                        <textarea value={cdi5body} onChange={(e) => {
                            setCdi5Body(e.target.value)
                        }}></textarea>
                        <button onClick={() => {
                            create_5(JSON.parse(cdi5body))
                        }}>Create
                        </button>
                        <button onClick={() => {
                            update_5(JSON.parse(cdi5body))
                        }}>Update
                        </button>
                    </div>
                    <div>
                        <textarea value={v2body} onChange={(e) => {
                            setV2Body(e.target.value)
                        }}></textarea>
                        <button onClick={() => {
                            create_v2(JSON.parse(v2body))
                        }}>Create
                        </button>
                        <button onClick={() => {
                            update_v2(JSON.parse(v2body))
                        }}>Update
                        </button>
                    </div>
                </div>

                <div className="error">{errorMsg}</div>
            </div>
    );
}
