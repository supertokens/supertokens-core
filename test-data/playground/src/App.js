import React, {useState} from "react";
import "./index.css";
import {
    backendCdi4Behaviour,
    cdi4CoreBehaviour,
    create_4,
    create_4_migrate,
    frontendCdi4Behaviour,
    get_4,
    update_4,
} from "./cdi4";
import {backendCdi5Behaviour, cdi5CoreBehaviour, create_5, frontendCdi5Behaviour, get_5, update_5,} from "./cdi5";
import {
    backendCdi51Behaviour,
    cdi51CoreBehaviour,
    create_v2,
    frontendCdi51Behaviour,
    get_v2,
    update_v2,
} from "./cdi51";
import {runTests} from "./tests";

export default function App(props) {
    const [tenantState, setTenantState] = useState({
        tenantId: "undefined",
        emailPasswordEnabled: false,
        passwordlessEnabled: false,
        thirdPartyEnabled: false,
        firstFactors: [],
        requiredSecondaryFactors: null,
    });

    const [errorMsg, setErrorMsg] = useState("");

    const [cdi4body, setCdi4Body] = useState('{\n  "tenantId": "public"\n}');
    const [cdi5body, setCdi5Body] = useState('{\n  "tenantId": "public"\n}');
    const [v2body, setV2Body] = useState('{\n  "tenantId": "public"\n}');

    return (
        <div className="App">
            <h3>DB State</h3>
            <pre>{JSON.stringify(tenantState, null, 2)}</pre>

            <div className="row">
                <div>
                    <h3>Get in 4.0</h3>
                    <pre>{JSON.stringify(get_4(tenantState), null, 2)}</pre>
                </div>
                <div>
                    <h3>Get in 5.0</h3>
                    <pre>{JSON.stringify(get_5(tenantState), null, 2)}</pre>
                </div>
                <div>
                    <h3>Get in v2</h3>
                    <pre>{JSON.stringify(get_v2(tenantState), null, 2)}</pre>
                </div>
            </div>

            <div className="row">
                <div>
                    <h4>Core behaviour</h4>
                    <pre>{cdi4CoreBehaviour(tenantState)}</pre>
                </div>

                <div>
                    <h4>Core behaviour</h4>
                    <pre>{cdi5CoreBehaviour(tenantState)}</pre>
                </div>

                <div>
                    <h4>Core behaviour</h4>
                    <pre>{cdi51CoreBehaviour(tenantState)}</pre>
                </div>
            </div>

            <div className="row">
                <div>
                    <h4>Backend behaviour</h4>
                    <pre>{backendCdi4Behaviour(tenantState)}</pre>
                </div>

                <div>
                    <h4>Backend behaviour</h4>
                    <pre>{backendCdi5Behaviour(tenantState)}</pre>
                </div>

                <div>
                    <h4>Backend behaviour</h4>
                    <pre>{backendCdi51Behaviour(tenantState)}</pre>
                </div>
            </div>

            <div className="row">
                <div>
                    <h4>Frontend behaviour</h4>
                    <pre>{frontendCdi4Behaviour(tenantState)}</pre>
                </div>

                <div>
                    <h4>Frontend behaviour</h4>
                    <pre>{frontendCdi5Behaviour(tenantState)}</pre>
                </div>

                <div>
                    <h4>Frontend behaviour</h4>
                    <pre>{frontendCdi51Behaviour(tenantState)}</pre>
                </div>
            </div>

            <div className="row">
                <h2>Create / Update</h2>
            </div>

            <div className="row">
                <div>
          <textarea
              value={cdi4body}
              onChange={(e) => {
                  setCdi4Body(e.target.value);
              }}
          ></textarea>
                    <button
                        onClick={() => {
                            setTenantState(create_4(JSON.parse(cdi4body)));
                        }}
                    >
                        Create
                    </button>
                    <button
                        onClick={() => {
                            setTenantState(create_4_migrate(JSON.parse(cdi4body)));
                        }}
                    >
                        Create &amp; migrate
                    </button>
                    <button
                        onClick={() => {
                            setTenantState(update_4(JSON.parse(cdi4body), tenantState));
                        }}
                    >
                        Update
                    </button>

                    <div className="shortcuts">
                        <h4>Shortcuts:</h4>

                        <p>
                            Tenant:
                            <span
                                className="clickable"
                                href="#"
                                onClick={(e) => {
                                    e.preventDefault();
                                    {
                                        let state = JSON.parse(cdi4body);
                                        state.tenantId = "public";
                                        setCdi4Body(JSON.stringify(state, null, 2));
                                    }
                                    {
                                        let state = JSON.parse(cdi5body);
                                        state.tenantId = "public";
                                        setCdi5Body(JSON.stringify(state, null, 2));
                                    }
                                    {
                                        let state = JSON.parse(v2body);
                                        state.tenantId = "public";
                                        setV2Body(JSON.stringify(state, null, 2));
                                    }
                                }}
                            >
                public
              </span>
                            &nbsp;
                            <span
                                className="clickable"
                                href="#"
                                onClick={(e) => {
                                    e.preventDefault();
                                    {
                                        let state = JSON.parse(cdi4body);
                                        state.tenantId = "t1";
                                        setCdi4Body(JSON.stringify(state, null, 2));
                                    }
                                    {
                                        let state = JSON.parse(cdi5body);
                                        state.tenantId = "t1";
                                        setCdi5Body(JSON.stringify(state, null, 2));
                                    }
                                    {
                                        let state = JSON.parse(v2body);
                                        state.tenantId = "t1";
                                        setV2Body(JSON.stringify(state, null, 2));
                                    }
                                }}
                            >
                t1
              </span>
                        </p>
                        <p>
                            emailPasswordEnabled:
                            <span
                                className="clickable"
                                href="#"
                                onClick={(e) => {
                                    e.preventDefault();
                                    let state = JSON.parse(cdi4body);
                                    state.emailPasswordEnabled = undefined;
                                    setCdi4Body(JSON.stringify(state, null, 2));
                                }}
                            >
                unset
              </span>
                            &nbsp;
                            <span
                                className="clickable"
                                href="#"
                                onClick={(e) => {
                                    e.preventDefault();
                                    let state = JSON.parse(cdi4body);
                                    state.emailPasswordEnabled = true;
                                    setCdi4Body(JSON.stringify(state, null, 2));
                                }}
                            >
                true
              </span>
                            &nbsp;
                            <span
                                className="clickable"
                                href="#"
                                onClick={(e) => {
                                    e.preventDefault();
                                    let state = JSON.parse(cdi4body);
                                    state.emailPasswordEnabled = false;
                                    setCdi4Body(JSON.stringify(state, null, 2));
                                }}
                            >
                false
              </span>
                        </p>
                        <p>
                            passwordlessEnabled:
                            <span
                                className="clickable"
                                href="#"
                                onClick={(e) => {
                                    e.preventDefault();
                                    let state = JSON.parse(cdi4body);
                                    state.passwordlessEnabled = undefined;
                                    setCdi4Body(JSON.stringify(state, null, 2));
                                }}
                            >
                unset
              </span>
                            &nbsp;
                            <span
                                className="clickable"
                                href="#"
                                onClick={(e) => {
                                    e.preventDefault();
                                    let state = JSON.parse(cdi4body);
                                    state.passwordlessEnabled = true;
                                    setCdi4Body(JSON.stringify(state, null, 2));
                                }}
                            >
                true
              </span>
                            &nbsp;
                            <span
                                className="clickable"
                                href="#"
                                onClick={(e) => {
                                    e.preventDefault();
                                    let state = JSON.parse(cdi4body);
                                    state.passwordlessEnabled = false;
                                    setCdi4Body(JSON.stringify(state, null, 2));
                                }}
                            >
                false
              </span>
                        </p>
                        <p>
                            thirdPartyEnabled:
                            <span
                                className="clickable"
                                href="#"
                                onClick={(e) => {
                                    e.preventDefault();
                                    let state = JSON.parse(cdi4body);
                                    state.thirdPartyEnabled = undefined;
                                    setCdi4Body(JSON.stringify(state, null, 2));
                                }}
                            >
                unset
              </span>
                            &nbsp;
                            <span
                                className="clickable"
                                href="#"
                                onClick={(e) => {
                                    e.preventDefault();
                                    let state = JSON.parse(cdi4body);
                                    state.thirdPartyEnabled = true;
                                    setCdi4Body(JSON.stringify(state, null, 2));
                                }}
                            >
                true
              </span>
                            &nbsp;
                            <span
                                className="clickable"
                                href="#"
                                onClick={(e) => {
                                    e.preventDefault();
                                    let state = JSON.parse(cdi4body);
                                    state.thirdPartyEnabled = false;
                                    setCdi4Body(JSON.stringify(state, null, 2));
                                }}
                            >
                false
              </span>
                        </p>
                    </div>
                </div>
                <div>
          <textarea
              value={cdi5body}
              onChange={(e) => {
                  setCdi5Body(e.target.value);
              }}
          ></textarea>

                    <button
                        onClick={() => {
                            setTenantState(create_5(JSON.parse(cdi5body)));
                        }}
                    >
                        Create
                    </button>
                    <button
                        onClick={() => {
                            setTenantState(update_5(JSON.parse(cdi5body), tenantState));
                        }}
                    >
                        Update
                    </button>

                    <div className="shortcuts">
                        <h4>Shortcuts:</h4>

                        <p>
                            Tenant:
                            <span
                                className="clickable"
                                href="#"
                                onClick={(e) => {
                                    e.preventDefault();
                                    {
                                        let state = JSON.parse(cdi4body);
                                        state.tenantId = "public";
                                        setCdi4Body(JSON.stringify(state, null, 2));
                                    }
                                    {
                                        let state = JSON.parse(cdi5body);
                                        state.tenantId = "public";
                                        setCdi5Body(JSON.stringify(state, null, 2));
                                    }
                                    {
                                        let state = JSON.parse(v2body);
                                        state.tenantId = "public";
                                        setV2Body(JSON.stringify(state, null, 2));
                                    }
                                }}
                            >
                public
              </span>
                            &nbsp;
                            <span
                                className="clickable"
                                href="#"
                                onClick={(e) => {
                                    e.preventDefault();
                                    {
                                        let state = JSON.parse(cdi4body);
                                        state.tenantId = "t1";
                                        setCdi4Body(JSON.stringify(state, null, 2));
                                    }
                                    {
                                        let state = JSON.parse(cdi5body);
                                        state.tenantId = "t1";
                                        setCdi5Body(JSON.stringify(state, null, 2));
                                    }
                                    {
                                        let state = JSON.parse(v2body);
                                        state.tenantId = "t1";
                                        setV2Body(JSON.stringify(state, null, 2));
                                    }
                                }}
                            >
                t1
              </span>
                        </p>
                        <p>
                            emailPasswordEnabled:
                            <span
                                className="clickable"
                                href="#"
                                onClick={(e) => {
                                    e.preventDefault();
                                    let state = JSON.parse(cdi5body);
                                    state.emailPasswordEnabled = undefined;
                                    setCdi5Body(JSON.stringify(state, null, 2));
                                }}
                            >
                unset
              </span>
                            &nbsp;
                            <span
                                className="clickable"
                                href="#"
                                onClick={(e) => {
                                    e.preventDefault();
                                    let state = JSON.parse(cdi5body);
                                    state.emailPasswordEnabled = true;
                                    setCdi5Body(JSON.stringify(state, null, 2));
                                }}
                            >
                true
              </span>
                            &nbsp;
                            <span
                                className="clickable"
                                href="#"
                                onClick={(e) => {
                                    e.preventDefault();
                                    let state = JSON.parse(cdi5body);
                                    state.emailPasswordEnabled = false;
                                    setCdi5Body(JSON.stringify(state, null, 2));
                                }}
                            >
                false
              </span>
                        </p>
                        <p>
                            passwordlessEnabled:
                            <span
                                className="clickable"
                                href="#"
                                onClick={(e) => {
                                    e.preventDefault();
                                    let state = JSON.parse(cdi5body);
                                    state.passwordlessEnabled = undefined;
                                    setCdi5Body(JSON.stringify(state, null, 2));
                                }}
                            >
                unset
              </span>
                            &nbsp;
                            <span
                                className="clickable"
                                href="#"
                                onClick={(e) => {
                                    e.preventDefault();
                                    let state = JSON.parse(cdi5body);
                                    state.passwordlessEnabled = true;
                                    setCdi5Body(JSON.stringify(state, null, 2));
                                }}
                            >
                true
              </span>
                            &nbsp;
                            <span
                                className="clickable"
                                href="#"
                                onClick={(e) => {
                                    e.preventDefault();
                                    let state = JSON.parse(cdi5body);
                                    state.passwordlessEnabled = false;
                                    setCdi5Body(JSON.stringify(state, null, 2));
                                }}
                            >
                false
              </span>
                        </p>
                        <p>
                            thirdPartyEnabled:
                            <span
                                className="clickable"
                                href="#"
                                onClick={(e) => {
                                    e.preventDefault();
                                    let state = JSON.parse(cdi5body);
                                    state.thirdPartyEnabled = undefined;
                                    setCdi5Body(JSON.stringify(state, null, 2));
                                }}
                            >
                unset
              </span>
                            &nbsp;
                            <span
                                className="clickable"
                                href="#"
                                onClick={(e) => {
                                    e.preventDefault();
                                    let state = JSON.parse(cdi5body);
                                    state.thirdPartyEnabled = true;
                                    setCdi5Body(JSON.stringify(state, null, 2));
                                }}
                            >
                true
              </span>
                            &nbsp;
                            <span
                                className="clickable"
                                href="#"
                                onClick={(e) => {
                                    e.preventDefault();
                                    let state = JSON.parse(cdi5body);
                                    state.thirdPartyEnabled = false;
                                    setCdi5Body(JSON.stringify(state, null, 2));
                                }}
                            >
                false
              </span>
                        </p>
                        <p>
                            firstFactors:
                            <span
                                className="clickable"
                                href="#"
                                onClick={(e) => {
                                    e.preventDefault();
                                    let state = JSON.parse(cdi5body);
                                    state.firstFactors = undefined;
                                    setCdi5Body(JSON.stringify(state, null, 2));
                                }}
                            >
                unset
              </span>
                            &nbsp;
                            <span
                                className="clickable"
                                href="#"
                                onClick={(e) => {
                                    e.preventDefault();
                                    let state = JSON.parse(cdi5body);
                                    state.firstFactors = null;
                                    setCdi5Body(JSON.stringify(state, null, 2));
                                }}
                            >
                null
              </span>
                            &nbsp;
                            <span
                                className="clickable"
                                href="#"
                                onClick={(e) => {
                                    e.preventDefault();
                                    let state = JSON.parse(cdi5body);
                                    state.firstFactors = ["emailpassword"];
                                    setCdi5Body(JSON.stringify(state, null, 2));
                                }}
                            >
                [emailpassword]
              </span>
                        </p>
                        <p>
                            requiredSecondaryFactors:
                            <span
                                className="clickable"
                                href="#"
                                onClick={(e) => {
                                    e.preventDefault();
                                    let state = JSON.parse(cdi5body);
                                    state.requiredSecondaryFactors = undefined;
                                    setCdi5Body(JSON.stringify(state, null, 2));
                                }}
                            >
                unset
              </span>
                            &nbsp;
                            <span
                                className="clickable"
                                href="#"
                                onClick={(e) => {
                                    e.preventDefault();
                                    let state = JSON.parse(cdi5body);
                                    state.requiredSecondaryFactors = null;
                                    setCdi5Body(JSON.stringify(state, null, 2));
                                }}
                            >
                null
              </span>
                            &nbsp;
                            <span
                                className="clickable"
                                href="#"
                                onClick={(e) => {
                                    e.preventDefault();
                                    let state = JSON.parse(cdi5body);
                                    state.requiredSecondaryFactors = ["otp-phone"];
                                    setCdi5Body(JSON.stringify(state, null, 2));
                                }}
                            >
                [otp-phone]
              </span>
                            <span
                                className="clickable"
                                href="#"
                                onClick={(e) => {
                                    e.preventDefault();
                                    let state = JSON.parse(cdi5body);
                                    state.requiredSecondaryFactors = ["emailpassword"];
                                    setCdi5Body(JSON.stringify(state, null, 2));
                                }}
                            >
                [emailpassword]
              </span>
                        </p>
                    </div>
                </div>
                <div>
          <textarea
              value={v2body}
              onChange={(e) => {
                  setV2Body(e.target.value);
              }}
          ></textarea>
                    <button
                        onClick={() => {
                            setTenantState(create_v2(JSON.parse(v2body)));
                        }}
                    >
                        Create
                    </button>
                    <button
                        onClick={() => {
                            setTenantState(update_v2(JSON.parse(v2body), tenantState));
                        }}
                    >
                        Update
                    </button>

                    <div className="shortcuts">
                        <h4>Shortcuts:</h4>

                        <p>
                            Tenant:
                            <span
                                className="clickable"
                                href="#"
                                onClick={(e) => {
                                    e.preventDefault();
                                    {
                                        let state = JSON.parse(cdi4body);
                                        state.tenantId = "public";
                                        setCdi4Body(JSON.stringify(state, null, 2));
                                    }
                                    {
                                        let state = JSON.parse(cdi5body);
                                        state.tenantId = "public";
                                        setCdi5Body(JSON.stringify(state, null, 2));
                                    }
                                    {
                                        let state = JSON.parse(v2body);
                                        state.tenantId = "public";
                                        setV2Body(JSON.stringify(state, null, 2));
                                    }
                                }}
                            >
                public
              </span>
                            &nbsp;
                            <span
                                className="clickable"
                                href="#"
                                onClick={(e) => {
                                    {
                                        let state = JSON.parse(cdi4body);
                                        state.tenantId = "t1";
                                        setCdi4Body(JSON.stringify(state, null, 2));
                                    }
                                    {
                                        let state = JSON.parse(cdi5body);
                                        state.tenantId = "t1";
                                        setCdi5Body(JSON.stringify(state, null, 2));
                                    }
                                    {
                                        let state = JSON.parse(v2body);
                                        state.tenantId = "t1";
                                        setV2Body(JSON.stringify(state, null, 2));
                                    }
                                }}
                            >
                t1
              </span>
                        </p>
                        <p>
                            firstFactors:
                            <span
                                className="clickable"
                                href="#"
                                onClick={(e) => {
                                    e.preventDefault();
                                    let state = JSON.parse(v2body);
                                    state.firstFactors = undefined;
                                    setV2Body(JSON.stringify(state, null, 2));
                                }}
                            >
                unset
              </span>
                            &nbsp;
                            <span
                                className="clickable"
                                href="#"
                                onClick={(e) => {
                                    e.preventDefault();
                                    let state = JSON.parse(v2body);
                                    state.firstFactors = null;
                                    setV2Body(JSON.stringify(state, null, 2));
                                }}
                            >
                null
              </span>
                            <span
                                className="clickable"
                                href="#"
                                onClick={(e) => {
                                    e.preventDefault();
                                    let state = JSON.parse(v2body);
                                    state.firstFactors = [];
                                    setV2Body(JSON.stringify(state, null, 2));
                                }}
                            >
                []
              </span>
                            &nbsp;
                            <span
                                className="clickable"
                                href="#"
                                onClick={(e) => {
                                    e.preventDefault();
                                    let state = JSON.parse(v2body);
                                    state.firstFactors = ["emailpassword"];
                                    setV2Body(JSON.stringify(state, null, 2));
                                }}
                            >
                [emailpassword]
              </span>
                        </p>
                        <p>
                            requiredSecondaryFactors:
                            <span
                                className="clickable"
                                href="#"
                                onClick={(e) => {
                                    e.preventDefault();
                                    let state = JSON.parse(v2body);
                                    state.requiredSecondaryFactors = undefined;
                                    setV2Body(JSON.stringify(state, null, 2));
                                }}
                            >
                unset
              </span>
                            &nbsp;
                            <span
                                className="clickable"
                                href="#"
                                onClick={(e) => {
                                    e.preventDefault();
                                    let state = JSON.parse(v2body);
                                    state.requiredSecondaryFactors = null;
                                    setV2Body(JSON.stringify(state, null, 2));
                                }}
                            >
                null
              </span>
                            &nbsp;
                            <span
                                className="clickable"
                                href="#"
                                onClick={(e) => {
                                    e.preventDefault();
                                    let state = JSON.parse(v2body);
                                    state.requiredSecondaryFactors = ["otp-phone"];
                                    setV2Body(JSON.stringify(state, null, 2));
                                }}
                            >
                [otp-phone]
              </span>
                        </p>
                    </div>
                </div>
            </div>

            <div>
                <p>Open console before running tests</p>
                <button
                    onClick={() => {
                        runTests();
                    }}
                >
                    Run tests
                </button>
            </div>
            <div className="error">{errorMsg}</div>
        </div>
    );
}
