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
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.webserver.api.accountlinking.*;
import io.supertokens.webserver.api.bulkimport.BulkImportAPI;
import io.supertokens.webserver.api.bulkimport.CountBulkImportUsersAPI;
import io.supertokens.webserver.api.bulkimport.DeleteBulkImportUserAPI;
import io.supertokens.webserver.api.bulkimport.ImportUserAPI;
import io.supertokens.webserver.api.core.*;
import io.supertokens.webserver.api.dashboard.*;
import io.supertokens.webserver.api.emailpassword.SignInAPI;
import io.supertokens.webserver.api.emailpassword.UserAPI;
import io.supertokens.webserver.api.emailpassword.*;
import io.supertokens.webserver.api.emailverification.GenerateEmailVerificationTokenAPI;
import io.supertokens.webserver.api.emailverification.RevokeAllTokensForUserAPI;
import io.supertokens.webserver.api.emailverification.UnverifyEmailAPI;
import io.supertokens.webserver.api.emailverification.VerifyEmailAPI;
import io.supertokens.webserver.api.jwt.JWKSAPI;
import io.supertokens.webserver.api.jwt.JWTSigningAPI;
import io.supertokens.webserver.api.multitenancy.*;
import io.supertokens.webserver.api.multitenancy.thirdparty.CreateOrUpdateThirdPartyConfigAPI;
import io.supertokens.webserver.api.multitenancy.thirdparty.RemoveThirdPartyConfigAPI;
import io.supertokens.webserver.api.oauth.*;
import io.supertokens.webserver.api.passwordless.*;
import io.supertokens.webserver.api.session.*;
import io.supertokens.webserver.api.thirdparty.GetUsersByEmailAPI;
import io.supertokens.webserver.api.thirdparty.SignInUpAPI;
import io.supertokens.webserver.api.totp.*;
import io.supertokens.webserver.api.useridmapping.RemoveUserIdMappingAPI;
import io.supertokens.webserver.api.useridmapping.UpdateExternalUserIdInfoAPI;
import io.supertokens.webserver.api.useridmapping.UserIdMappingAPI;
import io.supertokens.webserver.api.usermetadata.RemoveUserMetadataAPI;
import io.supertokens.webserver.api.usermetadata.UserMetadataAPI;
import io.supertokens.webserver.api.userroles.*;
import io.supertokens.webserver.api.webauthn.*;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.http.fileupload.FileUtils;
import org.jetbrains.annotations.TestOnly;

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
    private String CONTEXT_PATH = "";
    private final Main main;
    final PathRouter pathRouter;

    private final WebServerLogging logging;
    private TomcatReference tomcatReference;

    private Webserver(Main main) {
        this.main = main;
        this.logging = new WebServerLogging(main);
        this.pathRouter = new PathRouter(main);
    }

    public static Webserver getInstance(Main main) {
        try {
            return (Webserver) main.getResourceDistributor()
                    .getResource(new TenantIdentifier(null, null, null), RESOURCE_KEY);
        } catch (TenantOrAppNotFoundException e) {
            return (Webserver) main.getResourceDistributor()
                    .setResource(new TenantIdentifier(null, null, null), RESOURCE_KEY, new Webserver(main));
        }
    }

    public void start() {
        if (tomcatReference != null || Thread.currentThread() != main.getMainThread()) {
            return;
        }

        String tempDirLocation = decideTempDirLocation();
        File webserverTemp = new File(tempDirLocation);
        if (!webserverTemp.exists()) {
            webserverTemp.mkdirs();
        }


        CONTEXT_PATH = Config.getBaseConfig(main).getBasePath();

        // this will make it so that if there is a failure, then tomcat will throw an
        // error...
        System.setProperty("org.apache.catalina.startup.EXIT_ON_INIT_FAILURE", "true");

        Tomcat tomcat = new Tomcat();

        setupLogging();

        // baseDir is a place for Tomcat to store temporary files..
        tomcat.setBaseDir(tempDirLocation);

        // set thread pool size and port
        Connector connector = new Connector();
        connector.setProperty("maxThreads", Config.getBaseConfig(main).getMaxThreadPoolSize() + "");
        connector.setPort(Config.getBaseConfig(main).getPort(main));
        connector.setProperty("address", Config.getBaseConfig(main).getHost(main));

        tomcat.setConnector(connector);

        // we do this because we may run multiple tomcat servers in the same JVM
        tomcat.getEngine().setName(main.getProcessId());

        // create docBase folder and get context
        new File(decideTempDirLocation() + "webapps").mkdirs();
        StandardContext context = (StandardContext) tomcat.addContext(CONTEXT_PATH, "");

        // the amount of time for which we should wait for all requests to finish when
        // calling stop
        context.setUnloadDelay(5000);

        // start tomcat
        try {
            tomcat.start();
        } catch (LifecycleException e) {
            // reusing same port OR not right permissions given.
            Logging.error(main, TenantIdentifier.BASE_TENANT, null, false, e);
            throw new QuitProgramException(
                    "Error while starting webserver. Possible reasons:\n- Another instance of SuperTokens is already "
                            + "running on the same port. If you want to run another instance, please pass a new config "
                            + "file to it with a different port or specify the port via CLI options. \n- If you are "
                            + "running this on port 80 or 443, make "
                            + "sure to give the right permission to SuperTokens.\n- The provided host is not available"
                            + " on this server");
        }

        tomcatReference = new TomcatReference(tomcat, context);

        setupRoutes();
    }

    private String decideTempDirLocation(){
        String userSetTempDir = CLIOptions.get(main).getTempDirLocation();
        if(userSetTempDir != null && !userSetTempDir.endsWith(File.separator)) {
            userSetTempDir = userSetTempDir + File.separator;
        }
        String defaultTempDir = CLIOptions.get(main).getInstallationPath() + TEMP_FOLDER;
        return userSetTempDir != null ? userSetTempDir : defaultTempDir;
    }

    private void setupRoutes() {
        addAPI(new NotFoundOrHelloAPI(main));
        addAPI(new HelloAPI(main));
        addAPI(new JWKSPublicAPI(main));
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
        addAPI(new RecipeRouter(main, new UserAPI(main), new io.supertokens.webserver.api.thirdparty.UserAPI(main),
                new io.supertokens.webserver.api.passwordless.UserAPI(main)));
        addAPI(new GenerateEmailVerificationTokenAPI(main));
        addAPI(new VerifyEmailAPI(main));
        addAPI(new GetUsersByEmailAPI(main));
        addAPI(new SignInUpAPI(main));
        addAPI(new GetCodesAPI(main));
        addAPI(new DeleteCodesAPI(main));
        addAPI(new DeleteCodeAPI(main));
        addAPI(new CreateCodeAPI(main));
        addAPI(new CheckCodeAPI(main));
        addAPI(new ConsumeCodeAPI(main));
        addAPI(new TelemetryAPI(main));
        addAPI(new UsersCountAPI(main));
        addAPI(new ActiveUsersCountAPI(main));
        addAPI(new UsersAPI(main));
        addAPI(new DeleteUserAPI(main));
        addAPI(new RevokeAllTokensForUserAPI(main));
        addAPI(new UnverifyEmailAPI(main));
        addAPI(new JWTSigningAPI(main));
        addAPI(new JWKSAPI(main));
        addAPI(new UserMetadataAPI(main));
        addAPI(new RemoveUserMetadataAPI(main));
        addAPI(new CreateRoleAPI(main));
        addAPI(new AddUserRoleAPI(main));
        addAPI(new RemoveUserRoleAPI(main));
        addAPI(new GetRolesForUserAPI(main));
        addAPI(new GetUsersForRoleAPI(main));
        addAPI(new GetPermissionsForRoleAPI(main));
        addAPI(new RemovePermissionsForRoleAPI(main));
        addAPI(new GetRolesForPermissionAPI(main));
        addAPI(new RemoveRoleAPI(main));
        addAPI(new GetRolesAPI(main));
        addAPI(new UserIdMappingAPI(main));
        addAPI(new RemoveUserIdMappingAPI(main));
        addAPI(new CreateOrUpdateTotpDeviceAPI(main));
        addAPI(new VerifyTotpDeviceAPI(main));
        addAPI(new VerifyTotpAPI(main));
        addAPI(new RemoveTotpDeviceAPI(main));
        addAPI(new GetTotpDevicesAPI(main));
        addAPI(new ImportTotpDeviceAPI(main));
        addAPI(new UpdateExternalUserIdInfoAPI(main));
        addAPI(new ImportUserWithPasswordHashAPI(main));
        addAPI(new LicenseKeyAPI(main));
        addAPI(new EEFeatureFlagAPI(main));
        addAPI(new DashboardUserAPI(main));
        addAPI(new VerifyDashboardUserSessionAPI(main));
        addAPI(new DashboardSignInAPI(main));
        addAPI(new RevokeSessionAPI(main));
        addAPI(new GetDashboardUsersAPI(main));
        addAPI(new GetDashboardSessionsForUserAPI(main));
        addAPI(new SearchTagsAPI(main));

        addAPI(new CreateOrUpdateConnectionUriDomainAPI(main)); // deprecated
        addAPI(new CreateOrUpdateConnectionUriDomainV2API(main));
        addAPI(new RemoveConnectionUriDomainAPI(main));
        addAPI(new ListConnectionUriDomainsAPI(main)); // deprecated
        addAPI(new ListConnectionUriDomainsV2API(main));

        addAPI(new CreateOrUpdateAppAPI(main)); // deprecated
        addAPI(new CreateOrUpdateAppV2API(main));
        addAPI(new RemoveAppAPI(main));
        addAPI(new ListAppsAPI(main)); // deprecated
        addAPI(new ListAppsV2API(main));

        addAPI(new CreateOrUpdateTenantOrGetTenantAPI(main)); // deprecated
        addAPI(new CreateOrUpdateTenantOrGetTenantV2API(main));
        addAPI(new RemoveTenantAPI(main));
        addAPI(new ListTenantsAPI(main)); // deprecated
        addAPI(new ListTenantsV2API(main));

        addAPI(new CreateOrUpdateThirdPartyConfigAPI(main));
        addAPI(new RemoveThirdPartyConfigAPI(main));

        addAPI(new AssociateUserToTenantAPI(main));
        addAPI(new DisassociateUserFromTenant(main));

        addAPI(new GetUserByIdAPI(main));
        addAPI(new ListUsersByAccountInfoAPI(main));

        addAPI(new CanCreatePrimaryUserAPI(main));
        addAPI(new CreatePrimaryUserAPI(main));
        addAPI(new CanLinkAccountsAPI(main));
        addAPI(new LinkAccountsAPI(main));
        addAPI(new UnlinkAccountAPI(main));
        addAPI(new ConsumeResetPasswordAPI(main));

        addAPI(new RequestStatsAPI(main));
        addAPI(new GetTenantCoreConfigForDashboardAPI(main));

        addAPI(new BulkImportAPI(main));
        addAPI(new DeleteBulkImportUserAPI(main));
        addAPI(new ImportUserAPI(main));
        addAPI(new CountBulkImportUsersAPI(main));

        addAPI(new OAuthAuthAPI(main));
        addAPI(new OAuthTokenAPI(main));
        addAPI(new CreateUpdateOrGetOAuthClientAPI(main));
        addAPI(new OAuthClientListAPI(main));
        addAPI(new RemoveOAuthClientAPI(main));

        addAPI(new OAuthGetAuthConsentRequestAPI(main));
        addAPI(new OAuthAcceptAuthConsentRequestAPI(main));
        addAPI(new OAuthRejectAuthConsentRequestAPI(main));
        addAPI(new OAuthGetAuthLoginRequestAPI(main));
        addAPI(new OAuthAcceptAuthLoginRequestAPI(main));
        addAPI(new OAuthRejectAuthLoginRequestAPI(main));
        addAPI(new OAuthAcceptAuthLogoutRequestAPI(main));
        addAPI(new OAuthRejectAuthLogoutRequestAPI(main));
        addAPI(new OAuthTokenIntrospectAPI(main));

        addAPI(new RevokeOAuthTokenAPI(main));
        addAPI(new RevokeOAuthTokensAPI(main));
        addAPI(new RevokeOAuthSessionAPI(main));
        addAPI(new OAuthLogoutAPI(main));

        //webauthn
        addAPI(new OptionsRegisterAPI(main));
        addAPI(new SignInOptionsAPI(main));
        addAPI(new CredentialsRegisterAPI(main));
        addAPI(new SignUpWithCredentialRegisterAPI(main));
        addAPI(new GetGeneratedOptionsAPI(main));
        addAPI(new io.supertokens.webserver.api.webauthn.SignInAPI(main));
        addAPI(new ConsumeRecoverAccountTokenAPI(main));
        addAPI(new GenerateRecoverAccountTokenAPI(main));
        addAPI(new GetUserFromRecoverAccountTokenAPI(main));
        addAPI(new RemoveCredentialAPI(main));
        addAPI(new RemoveOptionsAPI(main));
        addAPI(new ListCredentialsAPI(main));
        addAPI(new GetCredentialAPI(main));
        addAPI(new UpdateUserEmailAPI(main));

        StandardContext context = tomcatReference.getContext();
        Tomcat tomcat = tomcatReference.getTomcat();

        tomcat.addServlet(CONTEXT_PATH, pathRouter.getPath(), pathRouter);
        context.addServletMappingDecoded(pathRouter.getPath(), pathRouter.getPath());
    }

    public void addAPI(WebserverAPI api) {
        this.pathRouter.addAPI(api);
    }

    public void stop() {
        if (tomcatReference != null && Thread.currentThread() == main.getMainThread()) {
            Tomcat tomcat = tomcatReference.getTomcat();
            if (tomcat.getServer() == null) {
                return;
            }
            Logging.info(main, TenantIdentifier.BASE_TENANT, "Stopping webserver...", true);
            if (tomcat.getServer().getState() != LifecycleState.DESTROYED) {
                if (tomcat.getServer().getState() != LifecycleState.STOPPED) {
                    try {
                        // this stops all new requests and waits for all requests to finish. The wait
                        // amount is defined by unloadDelay
                        tomcat.stop();
                    } catch (LifecycleException e) {
                        Logging.error(main, TenantIdentifier.BASE_TENANT, "Stop tomcat error.", false, e);
                    }
                }
                try {
                    tomcat.destroy();
                } catch (LifecycleException e) {
                    Logging.error(main, TenantIdentifier.BASE_TENANT, "Destroy tomcat error.", false, e);
                }
            }
        }

        // delete BASEDIR folder created by tomcat
        try {
            // we want to clear just this process' folder and not all since other processes
            // might still be running.
            FileUtils.deleteDirectory(new File(decideTempDirLocation()));
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

    @TestOnly
    public TomcatReference getTomcatReference(){
        return tomcatReference;
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

        @TestOnly
        public Tomcat getTomcatForTest(){
            return tomcat;
        }

        StandardContext getContext() {
            return context;
        }
    }
}
