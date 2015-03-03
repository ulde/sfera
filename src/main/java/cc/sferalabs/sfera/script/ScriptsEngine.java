package cc.sferalabs.sfera.script;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.EventListener;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import javax.script.Bindings;
import javax.script.Compilable;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cc.sferalabs.sfera.core.FilesWatcher;
import cc.sferalabs.sfera.core.Plugin;
import cc.sferalabs.sfera.core.Sfera;
import cc.sferalabs.sfera.core.Task;
import cc.sferalabs.sfera.events.BaseEvent;
import cc.sferalabs.sfera.script.parser.SferaScriptGrammarLexer;
import cc.sferalabs.sfera.script.parser.SferaScriptGrammarParser;
import cc.sferalabs.sfera.script.parser.SferaScriptGrammarParser.ParseContext;

import com.google.common.eventbus.Subscribe;

public class ScriptsEngine implements EventListener {

	public static final ScriptsEngine INSTANCE = new ScriptsEngine();
	private static final ScriptEngineManager SCRIPT_ENGINE_MANAGER = new ScriptEngineManager();
	private static final String SCRIPT_FILES_EXTENSION = ".ev";
	private static final Logger logger = LogManager.getLogger();

	private static HashMap<String, HashSet<Rule>> triggersActionsMap;
	private static HashMap<Path, List<String>> errors;

	/**
	 * 
	 * @return
	 */
	private static ScriptEngine getNewEngine() {
		return SCRIPT_ENGINE_MANAGER.getEngineByName("nashorn");
	}

	/**
	 * 
	 * @param event
	 */
	@Subscribe
	public synchronized static void executeActionsTriggeredBy(BaseEvent event) {
		try {
			Set<Rule> toExecute = new HashSet<Rule>();
			String id = event.getId();

			List<String> idParts = getParts(id);

			for (String idPart : idParts) {
				HashSet<Rule> triggeredActions = triggersActionsMap.get(idPart);
				if (triggeredActions != null) {
					for (Rule rule : triggeredActions) {
						if (rule.eval(event)) {
							toExecute.add(rule);
						}
					}
				}
			}

			for (Rule rule : toExecute) {
				rule.execute(event);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * 
	 * @param id
	 * @return
	 */
	private static List<String> getParts(String id) {
		List<String> parts = new ArrayList<String>();
		int dotIdx = id.length();
		do {
			String idPart = id.substring(0, dotIdx);
			parts.add(idPart);
			dotIdx = lastIndexOf(id, dotIdx - 1, '.', '(');
		} while (dotIdx > 0);

		return parts;
	}

	/**
	 * 
	 * @param string
	 * @param fromIndex
	 * @param chs
	 * @return
	 */
	private static int lastIndexOf(String string, int fromIndex, char... chs) {
		int i = Math.min(fromIndex, string.length() - 1);
		for (; i >= 0; i--) {
			for (char ch : chs) {
				if (string.charAt(i) == ch) {
					return i;
				}
			}
		}
		return -1;

	}

	/**
	 * 
	 * @param key
	 * @param value
	 */
	public synchronized static void putInGlobalScope(String key, Object value) {
		SCRIPT_ENGINE_MANAGER.put(key, value);
	}

	/**
	 * 
	 * @throws IOException
	 */
	public synchronized static void loadScriptFiles() throws IOException {
		try {
			triggersActionsMap = new HashMap<String, HashSet<Rule>>();
			errors = new HashMap<Path, List<String>>();

			loadScriptFilesIn(FileSystems.getDefault());
			for (Plugin plugin : Sfera.getPlugins().values()) {
				try (FileSystem pluginFs = FileSystems.newFileSystem(
						plugin.getPath(), null)) {
					loadScriptFilesIn(pluginFs);
				}
			}

			for (Entry<Path, List<String>> error : errors.entrySet()) {
				Path file = error.getKey();
				for (String message : error.getValue()) {
					logger.error("Errors in script file '{}': {}", file,
							message);
				}
			}
		} finally {
			try {
				Task reloadScriptFiles = new Task("Script files watcher") {

					@Override
					public void execute() {
						try {
							loadScriptFiles();
						} catch (IOException e) {
							logger.error("Error loading script files", e);
						}
					}
				};

				FilesWatcher.register(Paths.get("scripts"), reloadScriptFiles);
				FilesWatcher.register(Paths.get("plugins"), reloadScriptFiles);

			} catch (Exception e) {
				logger.error("Error registering script files watcher", e);
			}
		}
	}

	/**
	 * 
	 * @param fileSystem
	 * @throws IOException
	 */
	private static void loadScriptFilesIn(final FileSystem fileSystem)
			throws IOException {
		try {
			Files.walkFileTree(fileSystem.getPath("scripts"),
					new SimpleFileVisitor<Path>() {

						@Override
						public FileVisitResult preVisitDirectory(Path dir,
								BasicFileAttributes attrs) throws IOException {
							try (DirectoryStream<Path> stream = Files
									.newDirectoryStream(dir)) {
								createNewEnvironment(fileSystem, stream);
							}
							return FileVisitResult.CONTINUE;
						}
					});
		} catch (NoSuchFileException nsfe) {
		}
	}

	/**
	 * 
	 * @param fileSystem
	 * @param stream
	 */
	private static void createNewEnvironment(FileSystem fileSystem,
			DirectoryStream<Path> stream) {
		Compilable engine = (Compilable) getNewEngine();
		for (Path file : stream) {
			if (Files.isRegularFile(file)
					&& file.getFileName().toString()
							.endsWith(SCRIPT_FILES_EXTENSION)) {
				logger.info("Loading script file '{}'", file);
				try {
					parseScriptFile(file, fileSystem, engine);
				} catch (IOException e) {
					addError(file, "IOException: " + e.getLocalizedMessage());
				}
			}
		}
	}

	/**
	 * 
	 * @param scriptFile
	 * @param fileSystem
	 * @param engine
	 * @param localScope
	 * @throws IOException
	 */
	private static void parseScriptFile(Path scriptFile, FileSystem fileSystem,
			Compilable engine) throws IOException {
		try (BufferedReader r = Files.newBufferedReader(scriptFile,
				StandardCharsets.UTF_8)) {
			SferaScriptGrammarLexer lexer = new SferaScriptGrammarLexer(
					new ANTLRInputStream(r));
			CommonTokenStream tokens = new CommonTokenStream(lexer);
			SferaScriptGrammarParser parser = new SferaScriptGrammarParser(
					tokens);

			ScriptErrorListener grammarErrorListener = new ScriptErrorListener();
			lexer.removeErrorListeners();
			lexer.addErrorListener(grammarErrorListener);
			parser.removeErrorListeners();
			parser.addErrorListener(grammarErrorListener);

			ParseContext tree = parser.parse();

			if (!grammarErrorListener.errors.isEmpty()) {
				for (String e : grammarErrorListener.errors) {
					addError(scriptFile, e);
				}

				return;
			}

			ScriptEngine scriptEngine = (ScriptEngine) engine;

			ScriptLoader globalScriptLoader = new ScriptLoader(scriptEngine,
					fileSystem,
					scriptEngine.getBindings(ScriptContext.ENGINE_SCOPE));
			scriptEngine.put(ScriptLoader.VAR_NAME, globalScriptLoader);

			Bindings localScope = scriptEngine.createBindings();
			ScriptLoader localScriptLoader = new ScriptLoader(scriptEngine,
					fileSystem, localScope);
			localScope.put(ScriptLoader.VAR_NAME, localScriptLoader);
			String loggerName = scriptFile.toString();
			loggerName = loggerName.substring(0,
					loggerName.length() - SCRIPT_FILES_EXTENSION.length())
					.replace('/', '.');
			localScope.put("logger", LogManager.getLogger(loggerName));

			ScriptGrammarListener scriptListener = new ScriptGrammarListener(
					scriptFile, fileSystem, engine, localScope);
			ParseTreeWalker.DEFAULT.walk(scriptListener, tree);

			if (!scriptListener.errors.isEmpty()) {
				for (String e : scriptListener.errors) {
					addError(scriptFile, e);
				}

				return;
			}

			for (Entry<String, HashSet<Rule>> entry : scriptListener.triggerActionsMap
					.entrySet()) {
				String trigger = entry.getKey();
				HashSet<Rule> actions = triggersActionsMap.get(trigger);
				if (actions == null) {
					actions = new HashSet<Rule>();
					triggersActionsMap.put(trigger, actions);
				}
				actions.addAll(entry.getValue());
			}
		}
	}

	/**
	 * 
	 * @param file
	 * @param message
	 */
	private static void addError(Path file, String message) {
		List<String> messages = errors.get(file);
		if (messages == null) {
			messages = new ArrayList<String>();
			errors.put(file, messages);
		}
		messages.add(message);
	}
}
