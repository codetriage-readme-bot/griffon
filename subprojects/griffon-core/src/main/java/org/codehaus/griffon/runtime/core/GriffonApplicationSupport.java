/*
 * Copyright 2008-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.codehaus.griffon.runtime.core;

import griffon.core.*;
import griffon.core.artifact.*;
import griffon.core.env.Lifecycle;
import griffon.core.injection.Injector;
import griffon.core.resources.ResourcesInjector;
import org.codehaus.griffon.runtime.core.artifact.ArtifactImpl;
import org.codehaus.griffon.runtime.core.controller.NoopActionManager;
import org.codehaus.griffon.runtime.core.injection.NamedImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.beans.PropertyEditor;
import java.beans.PropertyEditorManager;
import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.List;
import java.util.Scanner;

import static griffon.core.GriffonExceptionHandler.sanitize;
import static griffon.util.GriffonNameUtils.isBlank;
import static java.util.Arrays.asList;
import static java.util.Objects.requireNonNull;

/**
 * Utility class for bootstrapping an application.
 *
 * @author Danno Ferrin
 * @author Andres Almiray
 */
public final class GriffonApplicationSupport {
    private static final Logger LOG = LoggerFactory.getLogger(GriffonApplicationSupport.class);
    private static final String ERROR_APPLICATION_NULL = "Argument 'application' cannot be null";

    private static final String KEY_APP_LIFECYCLE_HANDLER_DISABLE = "app.lifecycle.handler.disable";
    private static final String KEY_GRIFFON_CONTROLLER_ACTION_INTERCEPTOR_ORDER = "griffon.controller.action.interceptor.order";

    public static void init(@Nonnull GriffonApplication application) {
        requireNonNull(application, ERROR_APPLICATION_NULL);
        event(application, ApplicationEvent.BOOTSTRAP_START, asList(application));

        initializePropertyEditors(application);
        initializeResourcesInjector(application);
        runLifecycleHandler(Lifecycle.INITIALIZE, application);
        initializeArtifactManager(application);
        /*

        applyPlatformTweaks(app);
        initializeMvcManager(app);
        initializeAddonManager(app);
        */
        initializeActionManager(application);

        event(application, ApplicationEvent.BOOTSTRAP_END, asList(application));
    }

    protected static void event(@Nonnull GriffonApplication application, @Nonnull ApplicationEvent event, @Nullable List<?> args) {
        application.getEventRouter().publish(event.getName(), args);
    }

    private static void initializePropertyEditors(@Nonnull GriffonApplication application) {
        Enumeration<URL> urls = null;

        try {
            urls = applicationClassLoader(application).get().getResources("META-INF/editors/" + PropertyEditor.class.getName());
        } catch (IOException ioe) {
            return;
        }

        if (urls == null) return;

        while (urls.hasMoreElements()) {
            URL url = urls.nextElement();
            if (LOG.isDebugEnabled()) {
                LOG.debug("Reading " + PropertyEditor.class.getName() + " definitions from " + url);
            }

            try (Scanner scanner = new Scanner(url.openStream())) {
                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine();
                    if (line.startsWith("#") || isBlank(line)) return;
                    try {
                        String[] parts = line.trim().split("=");
                        Class targetType = loadClass(parts[0].trim(), applicationClassLoader(application).get());
                        Class editorClass = loadClass(parts[1].trim(), applicationClassLoader(application).get());
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Registering " + editorClass.getName() + " as editor for " + targetType.getName());
                        }
                        // Editor must have a no-args constructor
                        // CCE means the class can not be used
                        editorClass.newInstance();
                        PropertyEditorManager.registerEditor(targetType, editorClass);
                    } catch (Exception e) {
                        if (LOG.isWarnEnabled()) {
                            LOG.warn("Could not load PropertyEditor with " + line, sanitize(e));
                        }
                    }
                }
            } catch (IOException e) {
                if (LOG.isWarnEnabled()) {
                    LOG.warn("Could not load PropertyEditor definitions from " + url, sanitize(e));
                }
            }
        }

        Class[][] pairs = new Class[][]{
            new Class[]{Boolean.class, Boolean.TYPE},
            new Class[]{Byte.class, Byte.TYPE},
            new Class[]{Short.class, Short.TYPE},
            new Class[]{Integer.class, Integer.TYPE},
            new Class[]{Long.class, Long.TYPE},
            new Class[]{Float.class, Float.TYPE},
            new Class[]{Double.class, Double.TYPE}
        };

        for (Class[] pair : pairs) {
            PropertyEditor editor = PropertyEditorManager.findEditor(pair[0]);
            if (LOG.isDebugEnabled()) {
                LOG.debug("Registering " + editor.getClass().getName() + " as editor for " + pair[1].getName());
            }
            PropertyEditorManager.registerEditor(pair[1], editor.getClass());
        }
    }

    private static void initializeResourcesInjector(@Nonnull GriffonApplication application) {
        final ResourcesInjector injector = application.getInjector().getInstance(ResourcesInjector.class);
        application.getEventRouter().addEventListener(ApplicationEvent.NEW_INSTANCE.getName(), new CallableWithArgs<Void>() {
            public Void call(@Nonnull Object[] args) {
                Object instance = args[2];
                injector.injectResources(instance);
                return null;
            }
        });
    }

    private static void initializeArtifactManager(@Nonnull GriffonApplication application) {
        Injector<?> injector = application.getInjector();
        ArtifactManager artifactManager = application.getArtifactManager();
        artifactManager.registerArtifactHandler(injector.getInstance(ArtifactHandler.class, new ArtifactImpl(GriffonModel.class)));
        artifactManager.registerArtifactHandler(injector.getInstance(ArtifactHandler.class, new ArtifactImpl(GriffonView.class)));
        artifactManager.registerArtifactHandler(injector.getInstance(ArtifactHandler.class, new ArtifactImpl(GriffonController.class)));
        // TODO finish me!!
        artifactManager.loadArtifactMetadata(injector);
    }

    private static void initializeActionManager(final @Nonnull GriffonApplication application) {
        if (application.getActionManager() instanceof NoopActionManager) {
            return;
        }

        application.getEventRouter().addEventListener(ApplicationEvent.NEW_INSTANCE.getName(), new CallableWithArgs<Void>() {
            public Void call(@Nonnull Object[] args) {
                String type = (String) args[1];
                if (GriffonControllerClass.TYPE.equals(type)) {
                    GriffonController controller = (GriffonController) args[2];
                    application.getActionManager().createActions(controller);
                }
                return null;
            }
        });

        /*
        TODO finish me!!
        application.getEventRouter().addEventListener(ApplicationEvent.INITIALIZE_MVC_GROUP.getName(), new CallableWithArgs<Void>() {
            public Void call(@Nonnull Object[] args) {
                MVCGroupConfiguration groupConfig = (MVCGroupConfiguration) args[0];
                MVCGroup group = (MVCGroup) args[1];
                GriffonController controller = group.getController();
                if (controller == null) return null;
                FactoryBuilderSupport builder = group.getBuilder();
                Map<String, Action> actions = application.getActionManager().actionsFor(controller);
                for (Map.Entry<String, Action> action : actions.entrySet()) {
                    String actionKey = application.getActionManager().normalizeName(action.getKey()) + ActionManager.ACTION;
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("Adding action " + actionKey + " to " + groupConfig.getMvcType() + ":" + group.getMvcId() + ":builder");
                    }
                    builder.setVariable(actionKey, action.getValue().getToolkitAction());
                }
                return null;
            }
        });
        */

        /*
        Map<String, Map<String, Object>> actionInterceptors = new LinkedHashMap<>();
        for (GriffonAddon addon : application.getAddonManager().getAddons().values()) {
            Map<String, Map<String, Object>> interceptors = addon.getActionInterceptors();
            if (!interceptors.isEmpty()) {
                actionInterceptors.putAll(interceptors);
            }
        }

        // grab application specific order
        List<String> interceptorOrder = application.getApplicationConfiguration().get(KEY_GRIFFON_CONTROLLER_ACTION_INTERCEPTOR_ORDER, Collections.<String>emptyList());
        Map<String, Map<String, Object>> tmp = new LinkedHashMap<>(actionInterceptors);
        Map<String, Map<String, Object>> map = new LinkedHashMap<>();
        //noinspection ConstantConditions
        for (String interceptorName : interceptorOrder) {
            if (tmp.containsKey(interceptorName)) {
                map.put(interceptorName, tmp.remove(interceptorName));
            }
        }
        map.putAll(tmp);
        actionInterceptors.clear();
        actionInterceptors.putAll(map);
        if (LOG.isDebugEnabled()) {
            LOG.debug("Chosen interceptor order is " + map.keySet());
        }

        List<ActionInterceptor> sortedInterceptors = new ArrayList<>();
        Set<String> addedDeps = new LinkedHashSet<>();

        while (!map.isEmpty()) {
            int filtersAdded = 0;

            if (LOG.isDebugEnabled()) {
                LOG.debug("Current interceptor order is " + actionInterceptors.keySet());
            }

            for (Iterator<Map.Entry<String, Map<String, Object>>> iter = map.entrySet().iterator(); iter.hasNext(); ) {
                Map.Entry<String, Map<String, Object>> entry = iter.next();
                String interceptorName = entry.getKey();
                List<String> dependsOn = getConfigValue(entry.getValue(), "dependsOn", Collections.<String>emptyList());
                String interceptorClassName = getConfigValueAsString(entry.getValue(), "interceptor", null);

                if (LOG.isDebugEnabled()) {
                    LOG.debug("Processing interceptor '" + interceptorName + "'");
                    LOG.debug("    depends on '" + dependsOn + "'");
                }

                if (isBlank(interceptorClassName)) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("  Skipped interceptor '" + interceptorName + "', since it does not define an interceptor class");
                    }
                    iter.remove();
                    continue;
                }

                if (!dependsOn.isEmpty()) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("  Checking interceptor '" + interceptorName + "' dependencies (" + dependsOn.size() + ")");
                    }

                    boolean failedDep = false;
                    for (String dep : dependsOn) {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("  Checking interceptor '" + interceptorName + "' dependencies: " + dep);
                        }
                        if (!addedDeps.contains(dep)) {
                            // dep not in the list yet, we need to skip adding this to the list for now
                            if (LOG.isDebugEnabled()) {
                                LOG.debug("  Skipped interceptor '" + interceptorName + "', since dependency '" + dep + "' not yet added");
                            }
                            failedDep = true;
                            break;
                        } else {
                            if (LOG.isDebugEnabled()) {
                                LOG.debug("  Interceptor '" + interceptorName + "' dependency '" + dep + "' already added");
                            }
                        }
                    }

                    if (failedDep) {
                        // move on to next dependency
                        continue;
                    }
                }

                if (LOG.isDebugEnabled()) {
                    LOG.debug("  Adding interceptor '" + interceptorName + "', since all dependencies have been added");
                }
                sortedInterceptors.add((ActionInterceptor) newInstance(app, safeLoadClass(interceptorClassName)));
                addedDeps.add(interceptorName);
                iter.remove();
                filtersAdded++;
            }

            if (filtersAdded == 0) {
                // we have a cyclical dependency, warn the user and load in the order they appeared originally
                if (LOG.isWarnEnabled()) {
                    LOG.warn("::::::::::::::::::::::::::::::::::::::::::::::::::::::");
                    LOG.warn("::   Unresolved interceptor dependencies detected   ::");
                    LOG.warn("::   Continuing with original interceptor order     ::");
                    LOG.warn("::::::::::::::::::::::::::::::::::::::::::::::::::::::");
                }
                for (Map.Entry<String, Map<String, Object>> entry : map.entrySet()) {
                    String interceptorName = entry.getKey();
                    List<String> dependsOn = getConfigValue(entry.getValue(), "dependsOn", Collections.<String>emptyList());

                    // display this as a cyclical dep
                    if (LOG.isWarnEnabled()) {
                        LOG.warn("::   Interceptor " + interceptorName);
                    }
                    if (!dependsOn.isEmpty()) {
                        for (String dep : dependsOn) {
                            if (LOG.isWarnEnabled()) {
                                LOG.warn("::     depends on " + dep);
                            }
                        }
                    } else {
                        // we should only have items left in the list with deps, so this should never happen
                        // but a wise man once said...check for true, false and otherwise...just in case
                        if (LOG.isWarnEnabled()) {
                            LOG.warn("::   Problem while resolving dependencies.");
                            LOG.warn("::   Unable to resolve dependency hierarchy.");
                        }
                    }
                    if (LOG.isWarnEnabled()) {
                        LOG.warn("::::::::::::::::::::::::::::::::::::::::::::::::::::::");
                    }
                }
                break;
                // if we have processed all the interceptors, we are done
            } else if (sortedInterceptors.size() == actionInterceptors.size()) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Interceptor dependency ordering complete");
                }
                break;
            }
        }

        for (ActionInterceptor interceptor : sortedInterceptors) {
            application.getActionManager().addActionInterceptor(interceptor);
        }
        */
    }

    public static void runLifecycleHandler(@Nonnull Lifecycle lifecycle, @Nonnull GriffonApplication application) {
        requireNonNull(application, ERROR_APPLICATION_NULL);
        requireNonNull(lifecycle, "Argument 'lifecycle' cannot be null");

        boolean skipHandler = application.getApplicationConfiguration().getAsBoolean(KEY_APP_LIFECYCLE_HANDLER_DISABLE, false);
        if (skipHandler) {
            if (LOG.isDebugEnabled()) {
                LOG.info("Lifecycle handler '" + lifecycle.getName() + "' has been disabled. SKIPPING.");
            }
            return;
        }

        LifecycleHandler handler = null;
        try {
            handler = application.getInjector().getInstance(LifecycleHandler.class, new NamedImpl(lifecycle.getName()));
        } catch (Exception e) {
            e.printStackTrace();
            // the script must not exist, do nothing
            //LOGME - may be because of chained failures
            return;

        }

        handler.execute();
    }

    public static Class<?> loadClass(@Nonnull String className, @Nonnull ClassLoader classLoader) throws ClassNotFoundException {
        ClassNotFoundException cnfe = null;

        ClassLoader cl = GriffonApplicationSupport.class.getClassLoader();
        try {
            return cl.loadClass(className);
        } catch (ClassNotFoundException e) {
            cnfe = e;
        }

        cl = classLoader;
        try {
            return cl.loadClass(className);
        } catch (ClassNotFoundException e) {
            cnfe = e;
        }

        if (cnfe != null) throw cnfe;
        return null;
    }

    private static ApplicationClassLoader applicationClassLoader(@Nonnull GriffonApplication application) {
        return application.getInjector().getInstance(ApplicationClassLoader.class);
    }
}