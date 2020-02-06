/*
 * Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wso2.carbon.apimgt.gateway.utils;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import org.apache.commons.lang.StringUtils;
import org.apache.synapse.MessageContext;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.wso2.carbon.apimgt.impl.APIConstants;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public class OpenAPIUtils {

    /**
     * Return the resource authentication scheme of the API resource.
     *
     * @param openAPI OpenAPI of the API
     * @param synCtx  The message containing resource request
     * @return the resource authentication scheme
     */
    public static String getResourceAuthenticationScheme(OpenAPI openAPI, MessageContext synCtx) {
        String authType = null;
        Map<String, Object> vendorExtensions = getPathItemExtensions(synCtx, openAPI);
        if (vendorExtensions != null) {
            authType = (String) vendorExtensions.get(APIConstants.SWAGGER_X_AUTH_TYPE);
        }

        if (StringUtils.isNotBlank(authType)) {
            if (APIConstants.OASResourceAuthTypes.APPLICATION_OR_APPLICATION_USER.equals(authType)) {
                authType = APIConstants.AUTH_APPLICATION_OR_USER_LEVEL_TOKEN;
            } else if (APIConstants.OASResourceAuthTypes.APPLICATION_USER.equals(authType)) {
                authType = APIConstants.AUTH_APPLICATION_USER_LEVEL_TOKEN;
            } else if (APIConstants.OASResourceAuthTypes.NONE.equals(authType)) {
                authType = APIConstants.AUTH_NO_AUTHENTICATION;
            } else if (APIConstants.OASResourceAuthTypes.APPLICATION.equals(authType)) {
                authType = APIConstants.AUTH_APPLICATION_LEVEL_TOKEN;
            } else {
                authType = APIConstants.AUTH_APPLICATION_OR_USER_LEVEL_TOKEN;
            }
            return authType;
        }
        //Return 'Any' type (meaning security is on) if the authType is null or empty.
        return APIConstants.AUTH_APPLICATION_OR_USER_LEVEL_TOKEN;
    }

    /**
     * Return the scopes bound to the API resource.
     *
     * @param openAPI OpenAPI of the API
     * @param synCtx  The message containing resource request
     * @return the scopes
     */
    public static String getScopesOfResource(OpenAPI openAPI, MessageContext synCtx) {
        Map<String, Object> vendorExtensions = getPathItemExtensions(synCtx, openAPI);
        if (vendorExtensions != null) {
            String resourceScope = (String) vendorExtensions.get(APIConstants.SWAGGER_X_SCOPE);
            if (resourceScope == null) {
                // If x-scope not found in swagger, check for the scopes in security
                ArrayList<String> securityScopes = getPathItemSecurityScopes(synCtx, openAPI);
                if (securityScopes == null || securityScopes.isEmpty()) {
                    return null;
                } else {
                    return securityScopes.get(0);
                }
            } else {
                return resourceScope;
            }
        }
        return null;
    }

    /**
     * Return the roles of a given scope attached to a resource using the API swagger.
     *
     * @param openAPI OpenAPI of the API
     * @param synCtx  The message containing resource request
     * @param resourceScope  The scope of the resource
     * @return the roles of the scope in the comma separated format
     */
    public static String getRolesOfScope(OpenAPI openAPI, MessageContext synCtx, String resourceScope) {
        String resourceRoles = null;

        Map<String, Object> vendorExtensions = getPathItemExtensions(synCtx, openAPI);
        if (vendorExtensions != null) {
            if (StringUtils.isNotBlank(resourceScope)) {
                if (openAPI.getExtensions() != null &&
                        openAPI.getExtensions().get(APIConstants.SWAGGER_X_WSO2_SECURITY) != null) {
                    LinkedHashMap swaggerWSO2Security = (LinkedHashMap) openAPI.getExtensions()
                            .get(APIConstants.SWAGGER_X_WSO2_SECURITY);
                    if (swaggerWSO2Security != null &&
                            swaggerWSO2Security.get(APIConstants.SWAGGER_OBJECT_NAME_APIM) != null) {
                        LinkedHashMap swaggerObjectAPIM = (LinkedHashMap) swaggerWSO2Security
                                .get(APIConstants.SWAGGER_OBJECT_NAME_APIM);
                        if (swaggerObjectAPIM != null && swaggerObjectAPIM.get(APIConstants.SWAGGER_X_WSO2_SCOPES) != null) {
                            ArrayList<LinkedHashMap> apiScopes =
                                    (ArrayList<LinkedHashMap>) swaggerObjectAPIM.get(APIConstants.SWAGGER_X_WSO2_SCOPES);
                            for (LinkedHashMap scope: apiScopes) {
                                if (resourceScope.equals(scope.get(APIConstants.SWAGGER_SCOPE_KEY))) {
                                    resourceRoles = (String) scope.get(APIConstants.SWAGGER_ROLES);
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }

        if (resourceRoles == null) {
            Map<String, Object> extensions = openAPI.getComponents().getSecuritySchemes()
                    .get(APIConstants.SWAGGER_APIM_DEFAULT_SECURITY).getExtensions();
            if (extensions != null && extensions.get(APIConstants.SWAGGER_X_SCOPES_BINDINGS) != null) {
                LinkedHashMap<String, Object> scopeBindings =
                        (LinkedHashMap<String, Object>) extensions.get(APIConstants.SWAGGER_X_SCOPES_BINDINGS);
                if (scopeBindings != null) {
                    return (String) scopeBindings.get(resourceScope);
                }
            }
        }

        return null;
    }

    /**
     * Return the throttling tier of the API resource.
     *
     * @param openAPI OpenAPI of the API
     * @param synCtx  The message containing resource request
     * @return the resource throttling tier
     */
    public static String getResourceThrottlingTier(OpenAPI openAPI, MessageContext synCtx) {
        String throttlingTier = null;
        Map<String, Object> vendorExtensions = getPathItemExtensions(synCtx, openAPI);
        if (vendorExtensions != null) {
            throttlingTier = (String) vendorExtensions.get(APIConstants.SWAGGER_X_THROTTLING_TIER);
        }
        if (StringUtils.isNotBlank(throttlingTier)) {
            return throttlingTier;
        }
        return APIConstants.UNLIMITED_TIER;
    }

    private static Map<String, Object> getPathItemExtensions(MessageContext synCtx, OpenAPI openAPI) {
        if (openAPI != null) {
            String apiElectedResource = (String) synCtx.getProperty(APIConstants.API_ELECTED_RESOURCE);
            org.apache.axis2.context.MessageContext axis2MessageContext =
                    ((Axis2MessageContext) synCtx).getAxis2MessageContext();
            String httpMethod = (String) axis2MessageContext.getProperty(APIConstants.DigestAuthConstants.HTTP_METHOD);
            PathItem path = openAPI.getPaths().get(apiElectedResource);

            if (path != null) {
                switch (httpMethod) {
                    case APIConstants.HTTP_GET:
                        return path.getGet().getExtensions();
                    case APIConstants.HTTP_POST:
                        return path.getPost().getExtensions();
                    case APIConstants.HTTP_PUT:
                        return path.getPut().getExtensions();
                    case APIConstants.HTTP_DELETE:
                        return path.getDelete().getExtensions();
                    case APIConstants.HTTP_HEAD:
                        return path.getHead().getExtensions();
                    case APIConstants.HTTP_OPTIONS:
                        return path.getOptions().getExtensions();
                    case APIConstants.HTTP_PATCH:
                        return path.getPatch().getExtensions();
                }
            }
        }
        return null;
    }

    private static ArrayList<String> getPathItemSecurityScopes(MessageContext synCtx, OpenAPI openAPI) {
        if (openAPI != null) {
            String apiElectedResource = (String) synCtx.getProperty(APIConstants.API_ELECTED_RESOURCE);
            org.apache.axis2.context.MessageContext axis2MessageContext =
                    ((Axis2MessageContext) synCtx).getAxis2MessageContext();
            String httpMethod = (String) axis2MessageContext.getProperty(APIConstants.DigestAuthConstants.HTTP_METHOD);
            PathItem path = openAPI.getPaths().get(apiElectedResource);

            ArrayList<String> defaultSecurity =  (ArrayList<String>)openAPI.getSecurity().get(0).
                    get(APIConstants.SWAGGER_APIM_DEFAULT_SECURITY);
            if (path != null) {
                switch (httpMethod) {
                    case APIConstants.HTTP_GET:
                        return (ArrayList<String>) (path.getGet().getSecurity() != null &&
                                path.getGet().getSecurity().get(0) != null ? path.getGet().getSecurity().get(0).
                                get(APIConstants.SWAGGER_APIM_DEFAULT_SECURITY) : defaultSecurity);
                    case APIConstants.HTTP_POST:
                        return (ArrayList<String>) (path.getPost().getSecurity() != null &&
                                path.getPost().getSecurity().get(0) != null ? path.getPost().getSecurity().get(0).
                                get(APIConstants.SWAGGER_APIM_DEFAULT_SECURITY) : defaultSecurity);
                    case APIConstants.HTTP_PUT:
                        return (ArrayList<String>) (path.getPut().getSecurity() != null &&
                                path.getPut().getSecurity().get(0) != null ? path.getPut().getSecurity().get(0).
                                get(APIConstants.SWAGGER_APIM_DEFAULT_SECURITY) : defaultSecurity);
                    case APIConstants.HTTP_DELETE:
                        return (ArrayList<String>) (path.getDelete().getSecurity() != null &&
                                path.getDelete().getSecurity().get(0) != null ? path.getDelete().getSecurity().get(0).
                                get(APIConstants.SWAGGER_APIM_DEFAULT_SECURITY) : defaultSecurity);
                    case APIConstants.HTTP_HEAD:
                        return (ArrayList<String>) (path.getHead().getSecurity() != null &&
                                path.getHead().getSecurity().get(0) != null ? path.getHead().getSecurity().get(0).
                                get(APIConstants.SWAGGER_APIM_DEFAULT_SECURITY) : defaultSecurity);
                    case APIConstants.HTTP_OPTIONS:
                        return (ArrayList<String>) (path.getOptions().getSecurity() != null &&
                                path.getOptions().getSecurity().get(0) != null ? path.getOptions().getSecurity().get(0).
                                get(APIConstants.SWAGGER_APIM_DEFAULT_SECURITY) : defaultSecurity);
                    case APIConstants.HTTP_PATCH:
                        return (ArrayList<String>) (path.getPatch().getSecurity() != null &&
                                path.getPatch().getSecurity().get(0) != null ? path.getPatch().getSecurity().get(0).
                                get(APIConstants.SWAGGER_APIM_DEFAULT_SECURITY) : defaultSecurity);
                }
            }
        }
        return null;
    }
}
