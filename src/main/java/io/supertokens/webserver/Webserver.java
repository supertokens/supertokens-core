/*
 *    Copyright (c) 2020, VRAI Labs and/or its affiliates. All rights reserved.
 *
 *    This software is licensed under the Apache License, Version 2.0 (the
 *    "License") as published by the Apache Software Foundation.
 *
 *    You may not use this file except in compliance with the License. You may
 *    obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *    License for the specific language governing permissions and limitations
 *    under the License.
 */

package io.supertokens.webserver;

import io.supertokens.Main;
import io.supertokens.OperatingSystem;
import io.supertokens.ResourceDistributor;
import io.supertokens.cliOptions.CLIOptions;
import io.supertokens.config.Config;
import io.supertokens.exceptions.QuitProgramException;
import io.supertokens.output.Logging;
import io.supertokens.webserver.api.core.UsersAPI;
import io.supertokens.webserver.api.core.UsersCountAPI;
import io.supertokens.webserver.api.core.*;
import io.supertokens.webserver.api.emailpassword.*;
import io.supertokens.webserver.api.emailverification.GenerateEmailVerificationTokenAPI;
import io.supertokens.webserver.api.emailverification.RevokeAllTokensForUserAPI;
import io.supertokens.webserver.api.emailverification.UnverifyEmailAPI;
import io.supertokens.webserver.api.emailverification.VerifyEmailAPI;
import io.supertokens.webserver.api.jwt.JWKSAPI;
import io.supertokens.webserver.api.jwt.JWTSigningAPI;
import io.supertokens.webserver.api.passwordless.ConsumeCodeAPI;
import io.supertokens.webserver.api.passwordless.CreateCodeAPI;
import io.supertokens.webserver.api.session.*;
import io.supertokens.webserver.api.thirdparty.GetUsersByEmailAPI;
import io.supertokens.webserver.api.thirdparty.SignInUpAPI;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.http.fileupload.FileUtils;

import java.io.File;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.Logger;

public class Webserver extends ResourceDistributor.SingletonResource {

    private static final String RESOURCE_KEY = "io.supertokens.webserver.Webserver";
    private static final Object addLoggingHandlerLock = new Object();
    // we add the random UUI because we want to allow two instances of SuperTokens
    // to run (on different ports) and their tomcat servers should not affect each
    // other.
    private final String TEMP_FOLDER = OperatingSystem.getOS() == OperatingSystem.OS.WINDOWS
            ? "webserver-temp\\" + UUID.randomUUID().toString() + "\\"
            : "webserver-temp/" + UUID.randomUUID().toString() + "/";
    // contextPath is the prefix to all paths for all URLs. So it's "" for us.
    private final String CONTEXT_PATH = "";
    private final Main main;

    private final WebServerLogging logging;
    private TomcatReference tomcatReference;

    private Webserver(Main main) {
        this.main = main;
        this.logging = new WebServerLogging(main);
    }

    public static Webserver getInstance(Main main) {
        Webserver instance = (Webserver) main.getResourceDistributor().getResource(RESOURCE_KEY);
        if (instance == null) {
            instance = (Webserver) main.getResourceDistributor().setResource(RESOURCE_KEY, new Webserver(main));
        }
        return instance;
    }

    public void start() {
        if (tomcatReference != null || Thread.currentThread() != main.getMainThread()) {
            return;
        }

        File webserverTemp = new File(CLIOptions.get(main).getInstallationPath() + "webserver-temp");
        if (!webserverTemp.exists()) {
            webserverTemp.mkdir();
        }

        // this will make it so that if there is a failure, then tomcat will throw an
        // error...
        System.setProperty("org.apache.catalina.startup.EXIT_ON_INIT_FAILURE", "true");

        Tomcat tomcat = new Tomcat();

        setupLogging();

        // baseDir is a place for Tomcat to store temporary files..
        tomcat.setBaseDir(CLIOptions.get(main).getInstallationPath() + TEMP_FOLDER);

        // set thread pool size and port
        Connector connector = new Connector();
        connector.setAttribute("maxThreads", Config.getConfig(main).getMaxThreadPoolSize());
        connector.setPort(Config.getConfig(main).getPort(main));
        connector.setAttribute("address", Config.getConfig(main).getHost(main));

        tomcat.setConnector(connector);

        // we do this because we may run multiple tomcat servers in the same JVM
        tomcat.getEngine().setName(main.getProcessId());

        // create docBase folder and get context
        new File(CLIOptions.get(main).getInstallationPath() + TEMP_FOLDER + "webapps").mkdirs();
        StandardContext context = (StandardContext) tomcat.addContext(CONTEXT_PATH, "");

        // the amount of time for which we should wait for all requests to finish when
        // calling stop
        context.setUnloadDelay(5000);

        // start tomcat
        try {
            tomcat.start();
        } catch (LifecycleException e) {
            // reusing same port OR not right permissions given.
            Logging.error(main, null, false, e);
            throw new QuitProgramException(
                    "Error while starting webserver. Possible reasons:\n- Another instance of SuperTokens is already "
                            + "running on the same port. If you want to run another instance, please pass a new config "
                            + "file to it with a different port or specify the port via CLI options. \n- If you are "
                            + "running this on port 80 or 443, make "
                            + "sure to give the right permission to SuperTokens.\n- The provided host is not available"
                            + " on this server");
        }

        tomcatReference = new TomcatReference(tomcat, context);

        try {
            setupRoutes();
        } catch (Exception e) {
            Logging.error(main, null, false, e);
            throw new QuitProgramException("API routes not initialised properly: " + e.getMessage());
        }
    }

    private void setupRoutes() throws Exception {
        addAPI(new NotFoundAPI(main));
        addAPI(new HelloAPI(main));
        addAPI(new SessionAPI(main));
        addAPI(new VerifySessionAPI(main));
        addAPI(new RefreshSessionAPI(main));
        addAPI(new SessionUserAPI(main));
        addAPI(new SessionDataAPI(main));
        addAPI(new ConfigAPI(main));
        addAPI(new HandshakeAPI(main));
        addAPI(new SessionRemoveAPI(main));
        addAPI(new ApiVersionAPI(main));
        addAPI(new SessionRegenerateAPI(main));
        addAPI(new JWTDataAPI(main));
        addAPI(new SignUpAPI(main));
        addAPI(new SignInAPI(main));
        addAPI(new GeneratePasswordResetTokenAPI(main));
        addAPI(new ResetPasswordAPI(main));
        addAPI(new RecipeRouter(main, new UserAPI(main), new io.supertokens.webserver.api.thirdparty.UserAPI(main)));
        addAPI(new GenerateEmailVerificationTokenAPI(main));
        addAPI(new VerifyEmailAPI(main));
        addAPI(new GetUsersByEmailAPI(main));
        addAPI(new SignInUpAPI(main));
        addAPI(new CreateCodeAPI(main));
        addAPI(new ConsumeCodeAPI(main));
        addAPI(new TelemetryAPI(main));
        addAPI(new UsersCountAPI(main));
        addAPI(new UsersAPI(main));
        addAPI(new RevokeAllTokensForUserAPI(main));
        addAPI(new UnverifyEmailAPI(main));
        addAPI(new JWTSigningAPI(main));
        addAPI(new JWKSAPI(main));

        // deprecated APIs:
        addAPI(new RecipeRouter(main, new io.supertokens.webserver.api.emailpassword.UsersAPI(main),
                new io.supertokens.webserver.api.thirdparty.UsersAPI(main)));
        addAPI(new RecipeRouter(main, new io.supertokens.webserver.api.emailpassword.UsersCountAPI(main),
                new io.supertokens.webserver.api.thirdparty.UsersCountAPI(main)));
    }

    public void addAPI(WebserverAPI api) {
        StandardContext context = tomcatReference.getContext();
        Tomcat tomcat = tomcatReference.getTomcat();

        tomcat.addServlet(CONTEXT_PATH, api.getPath(), api);
        context.addServletMappingDecoded(api.getPath(), api.getPath());
    }

    public void stop() {
        if (tomcatReference != null && Thread.currentThread() == main.getMainThread()) {
            Tomcat tomcat = tomcatReference.getTomcat();
            if (tomcat.getServer() == null) {
                return;
            }
            Logging.info(main, "Stopping webserver...");
            if (tomcat.getServer().getState() != LifecycleState.DESTROYED) {
                if (tomcat.getServer().getState() != LifecycleState.STOPPED) {
                    try {
                        // this stops all new requests and waits for all requests to finish. The wait
                        // amount is defined by unloadDelay
                        tomcat.stop();
                    } catch (LifecycleException e) {
                        Logging.error(main, "Stop tomcat error.", false, e);
                    }
                }
                try {
                    tomcat.destroy();
                } catch (LifecycleException e) {
                    Logging.error(main, "Destroy tomcat error.", false, e);
                }
            }
        }

        // delete BASEDIR folder created by tomcat
        try {
            // we want to clear just this process' folder and not all since other processes
            // might still be running.
            FileUtils.deleteDirectory(new File(CLIOptions.get(main).getInstallationPath() + TEMP_FOLDER));
        } catch (Exception ignored) {
        }

        tomcatReference = null;
    }

    private void setupLogging() {

        /*
         * NOTE: The log this produces is only accurate in production or development.
         *
         * For testing, it may happen that multiple processes are running at the same
         * time which can lead to one of them being the winner and its main instance
         * being attached to logger class. This would yield inaccurate processIds during
         * logging.
         *
         * Finally, during testing, the winner's logger might be removed, in which case
         * nothing will be handling logging and tomcat's logs would not be outputed
         * anywhere.
         *
         */

        synchronized (addLoggingHandlerLock) {
            Logger logger = Logger.getLogger("org.apache");
            if (logger.getHandlers().length > 0) {
                // a handler already exists, so we don't need to add one again.
                return;
            }

            Handler[] parentHandler = logger.getParent().getHandlers();
            for (Handler handler : parentHandler) {
                logger.getParent().removeHandler(handler);
            }
            logger.setUseParentHandlers(false);
            logger.addHandler(this.logging);
        }
    }

    public void closeLogger() {
        synchronized (addLoggingHandlerLock) {
            Logger logger = Logger.getLogger("org.apache");
            logger.removeHandler(this.logging);
        }
    }

    public static class TomcatReference {
        private Tomcat tomcat;
        private StandardContext context;

        TomcatReference(Tomcat tomcat, StandardContext context) {
            this.tomcat = tomcat;
            this.context = context;
        }

        Tomcat getTomcat() {
            return tomcat;
        }

        StandardContext getContext() {
            return context;
        }
    }
}
