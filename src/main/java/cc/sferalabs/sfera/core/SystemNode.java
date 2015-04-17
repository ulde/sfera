package cc.sferalabs.sfera.core;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.EventListener;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cc.sferalabs.sfera.apps.Application;
import cc.sferalabs.sfera.core.events.SystemStateEvent;
import cc.sferalabs.sfera.drivers.Driver;
import cc.sferalabs.sfera.events.Bus;
import cc.sferalabs.sfera.events.Node;
import cc.sferalabs.sfera.script.ScriptsEngine;

import com.google.common.eventbus.Subscribe;

public class SystemNode implements Node, EventListener {

	private static ConcurrentHashMap<String, Plugin> plugins;

	private static List<Driver> drivers;
	private static List<Application> applications;

	private static final ServiceLoader<SferaService> SERVICE_LOADER = ServiceLoader
			.load(SferaService.class);

	private static final Logger logger = LogManager.getLogger();

	public static final SystemNode INSTANCE = new SystemNode();

	private static final String BASE_PACKAGE = "cc.sferalabs.sfera";
	private static final String DEFAULT_DRIVERS_PACKAGE = BASE_PACKAGE
			+ ".drivers";
	private static final String DEFAULT_APPS_PACKAGE = BASE_PACKAGE + ".apps";

	/**
	 * 
	 */
	private SystemNode() {
	}

	/**
	 * 
	 */
	void start() {
		logger.info("======== Started ========");

		Bus.post(new SystemStateEvent("start"));
		init();
		Bus.register(this);
		Bus.post(new SystemStateEvent("ready"));
	}

	@Override
	public String getId() {
		return "system";
	}

	@Subscribe
	public void handleSystemStateEvent(SystemStateEvent event) {
		String state = event.getValue();
		if ("quit".equals(state)) {
			quit();
			logger.info("Quitted");
			System.exit(0);
		}
	}

	/**
	 * 
	 */
	private void init() {
		try {
			Configuration.load();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		for (SferaService service : SERVICE_LOADER) {
			try {
				String name = service.getName();
				logger.debug("Initializing service {}...", name);
				service.init();
				logger.debug("Service {} initiated", name);
			} catch (Exception e) {
				logger.error("Error initiating service '" + service.getClass()
						+ "'", e);
			}
		}
		
		loadPlugins();
		loadDrivers();
		loadApplications();

		for (final Driver d : drivers) {
			d.start();
		}
	}

	/**
	 * 
	 */
	private void quit() {
		logger.warn("System stopped");

		logger.debug("Quitting drivers...");
		for (final Driver d : drivers) {
			d.disable();
		}

		logger.debug("Waiting 5 seconds for drivers to quit...");
		try {
			Thread.sleep(5000);
		} catch (InterruptedException e) {
		}

		logger.debug("terminating tasks...");
		try {
			TasksManager.DEFAULT.getExecutorService().shutdownNow();
			TasksManager.DEFAULT.getExecutorService().awaitTermination(5,
					TimeUnit.SECONDS);
		} catch (InterruptedException e) {
		}
		logger.debug("Tasks terminated");

		for (SferaService service : SERVICE_LOADER) {
			try {
				String name = service.getName();
				logger.debug("Quitting service {}...", name);
				service.quit();
				logger.debug("Service {} quitted", name);
			} catch (Exception e) {
				logger.error("Error quitting service '" + service.getClass()
						+ "'", e);
			}
		}
	}

	/**
	 * 
	 * @throws IOException
	 */
	private void loadPlugins() {
		plugins = new ConcurrentHashMap<>();
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths
				.get("plugins"))) {
			for (Path file : stream) {
				try {
					if (!Files.isHidden(file)) {
						Plugin p = new Plugin(file);
						plugins.put(p.getId(), p);
						logger.info("Plugin '{}' loaded", p.getId());
					}
				} catch (Exception e) {
					logger.error("Error loading file '" + file
							+ "' in plugins folder", e);
				}
			}
		} catch (NoSuchFileException e) {
			logger.debug("No plugins directory found");
		} catch (IOException e) {
			logger.error("Error loading plugins", e);
		}
	}

	/**
	 * 
	 */
	private void loadDrivers() {
		drivers = new ArrayList<Driver>();
		Properties props = Configuration.getProperties();
		for (String propName : props.stringPropertyNames()) {
			if (!propName.contains(".")) {
				String driverClass = props.getProperty(propName);
				try {
					if (!driverClass.contains(".")) {
						driverClass = DEFAULT_DRIVERS_PACKAGE + "."
								+ driverClass.toLowerCase() + "." + driverClass;
					}
					Class<?> clazz = Class.forName(driverClass);
					Constructor<?> constructor = clazz
							.getConstructor(new Class[] { String.class });
					Driver driverInstance = (Driver) constructor
							.newInstance(propName);
					drivers.add(driverInstance);
					ScriptsEngine.putObjectInGlobalScope(
							driverInstance.getId(), driverInstance);
					if (driverInstance instanceof EventListener) {
						Bus.register((EventListener) driverInstance);
					}
					logger.info("Driver '{}' of type '{}' instantiated",
							propName, driverClass);
				} catch (Throwable e) {
					logger.error("Error instantiating driver '" + propName
							+ "' of type '" + driverClass + "'", e);
				}
			}
		}
	}

	/**
	 * 
	 */
	private void loadApplications() {
		applications = new ArrayList<Application>();
		String appList = Configuration.SYSTEM.getProperty("apps", null);
		if (appList != null) {
			for (String appClass : appList.split(",")) {
				appClass = appClass.trim();
				try {
					if (!appClass.contains(".")) {
						appClass = DEFAULT_APPS_PACKAGE + "."
								+ appClass.toLowerCase() + "." + appClass;
					}
					Class<?> clazz = Class.forName(appClass);
					Constructor<?> constructor = clazz.getConstructor();
					Application appInstance = (Application) constructor
							.newInstance();
					applications.add(appInstance);
					if (appInstance instanceof EventListener) {
						Bus.register((EventListener) appInstance);
					}
					logger.info("App '{}' loaded", appClass);
				} catch (Throwable e) {
					logger.error("Error instantiating app: " + appClass, e);
				}
			}
		}
	}

	/**
	 * 
	 * @return
	 */
	public static Map<String, Plugin> getPlugins() {
		return plugins;
	}

}
