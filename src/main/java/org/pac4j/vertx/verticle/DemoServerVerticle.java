/*
  Copyright 2014 - 2015 pac4j organization

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */

package org.pac4j.vertx.verticle;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.SessionHandler;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.handler.UserSessionHandler;
import io.vertx.ext.web.sstore.LocalSessionStore;
import io.vertx.ext.web.sstore.SessionStore;
import org.pac4j.core.config.Config;
import org.pac4j.core.context.HttpConstants;
import org.pac4j.vertx.auth.Pac4jAuthProvider;
import org.pac4j.vertx.config.Pac4jConfigurationFactory;
import org.pac4j.vertx.handler.DemoHandlers;
import org.pac4j.vertx.handler.impl.CallbackHandler;
import org.pac4j.vertx.handler.impl.Pac4jAuthHandlerOptions;

/**
 * Stateful server example.
 * 
 * @author Jeremy Prime
 * @since 2.0.0
 *
 */
public class DemoServerVerticle extends AbstractVerticle {

    private static final Logger LOG = LoggerFactory.getLogger(DemoServerVerticle.class);
    private final Handler<RoutingContext> protectedIndexRenderer = DemoHandlers.protectedIndexHandler();
    private final Pac4jAuthProvider authProvider = new Pac4jAuthProvider(); // We don't need to instantiate this on demand
    private Config config = null;

    @Override
    public void start() {

        final Router router = Router.router(vertx);
        SessionStore sessionStore = LocalSessionStore.create(vertx);
        SessionHandler sessionHandler = SessionHandler.create(sessionStore);

        router.route().handler(io.vertx.ext.web.handler.CookieHandler.create());
        router.route().handler(sessionHandler);
        router.route().handler(UserSessionHandler.create(authProvider));

        router.route().failureHandler(rc -> {
            final int statusCode = rc.statusCode();
            rc.response().setStatusCode(statusCode > 0 ? statusCode : 500); // use status code 500 in the event that vert.x hasn't set one,
            // as we've failed for an unspecified reason - which implies internal server error

            switch (rc.response().getStatusCode()) {

                case HttpConstants.FORBIDDEN:
                    rc.response().sendFile("static/error403.html");
                    break;

                case HttpConstants.UNAUTHORIZED:
                    rc.response().sendFile("static/error401.html");
                    break;

                case 500:
                    rc.response().sendFile("static/error500.html");
                    break;

                default:
                    rc.response().end();

            }
        });


        // need to add a json configuration file internally and ensure it's consumed by this verticle
        LOG.info("DemoServerVerticle: config is \n" + config().encodePrettily());
        config = new Pac4jConfigurationFactory(config(), vertx, sessionStore).build();

        // Facebook-authenticated endpoints
        addProtectedEndpointWithoutAuthorizer("/facebook/index.html", "FacebookClient", router);
        addProtectedEndpoint("/facebookadmin/index.html", "FacebookClient", Pac4jConfigurationFactory.AUTHORIZER_ADMIN, router);
        addProtectedEndpoint("/facebookcustom/index.html", "FacebookClient", Pac4jConfigurationFactory.AUTHORIZER_CUSTOM, router);

        // Twitter/facebook-authenticated endpoints
        addProtectedEndpointWithoutAuthorizer("/twitter/index.html", "TwitterClient,FacebookClient", router);

        // Form-authenticated endpoint
        addProtectedEndpointWithoutAuthorizer("/form/index.html", "FormClient", router);

        // Form-protected AJAX endpoint
        Pac4jAuthHandlerOptions options = new Pac4jAuthHandlerOptions().withClientName("FormClient");
        final String ajaxProtectedUrl = "/form/index.html.json";
        router.get(ajaxProtectedUrl).handler(DemoHandlers.authHandler(vertx, config, authProvider,
                options));
        router.get(ajaxProtectedUrl).handler(DemoHandlers.formIndexJsonHandler());

        // Indirect basic auth-protected endpoint
        addProtectedEndpointWithoutAuthorizer("/basicauth/index.html", "IndirectBasicAuthClient", router);

        // Cas-authenticated endpoint
        addProtectedEndpointWithoutAuthorizer("/cas/index.html", "CasClient", router);

        // Oidc-authenticated endpoint
        addProtectedEndpointWithoutAuthorizer("/oidc/index.html", "OidcClient", router);

        // Saml-authenticated endpoint
        addProtectedEndpointWithoutAuthorizer("/saml/index.html", "SAML2Client", router);

        // Requires authentication endpoint without specific authenticator attached
        addProtectedEndpointWithoutAuthorizer("/protected/index.html", "", router);

        // Direct basic auth authentication (web services)
        addProtectedEndpointWithoutAuthorizer("/dba/index.html", "DirectBasicAuthClient,ParameterClient", router);
        Pac4jAuthHandlerOptions dbaEndpointOptions = new Pac4jAuthHandlerOptions().withClientName("DirectBasicAuthClient,ParameterClient");
        router.post("/dba/index.html").handler(DemoHandlers.authHandler(vertx, config, authProvider,
                dbaEndpointOptions));
        router.post("/dba/index.html").handler(protectedIndexRenderer);


        // Direct basic auth then token authentication (web services)
        addProtectedEndpointWithoutAuthorizer("/rest-jwt/index.html", "ParameterClient", router);

        router.get("/index.html").handler(DemoHandlers.indexHandler());

        final CallbackHandler callbackHandler = new CallbackHandler(vertx, config);
        router.get("/callback").handler(callbackHandler); // This will deploy the callback handler
        router.post("/callback").handler(BodyHandler.create().setMergeFormAttributes(true));
        router.post("/callback").handler(callbackHandler);

        router.get("/logout").handler(DemoHandlers.logoutHandler());

        router.get("/loginForm").handler(DemoHandlers.loginFormHandler(config));
        router.get("/jwt.html").handler(DemoHandlers.jwtGenerator(config()));
        router.get("/").handler(DemoHandlers.indexHandler());
        router.get("/*").handler(StaticHandler.create("static"));

        vertx.createHttpServer()
                .requestHandler(router::accept)
                .listen(8080);
    }

    private void addProtectedEndpointWithoutAuthorizer(final String url, final String clientNames, final Router router) {
        addProtectedEndpoint(url, clientNames, null, router);
    }

    private void addProtectedEndpoint(final String url, final String clientNames, final String authName, final Router router) {
        Pac4jAuthHandlerOptions options = new Pac4jAuthHandlerOptions().withClientName(clientNames);
        if (authName != null) {
            options = options.withAuthorizerName(authName);
        }
        router.get(url).handler(DemoHandlers.authHandler(vertx, config, authProvider,
                options));
        router.get(url).handler(protectedIndexRenderer);
    }

}
