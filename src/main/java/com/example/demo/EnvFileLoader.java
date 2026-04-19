package com.example.demo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

final class EnvFileLoader {

	private static final String DOTENV_FILE_NAME = ".env";
	private static final String DOTENV_EXAMPLE_FILE_NAME = ".env.example";

	private EnvFileLoader() {
	}

	static void load() {
		Path dotenvPath = resolveEnvFile();
		if (dotenvPath == null) {
			return;
		}

		try {
			List<String> lines = Files.readAllLines(dotenvPath, StandardCharsets.UTF_8);
			for (String line : lines) {
				parseAndApply(line);
			}
		}
		catch (IOException exception) {
			throw new IllegalStateException("Failed to load .env file", exception);
		}
	}

	private static Path resolveEnvFile() {
		Path dotenvPath = Path.of(DOTENV_FILE_NAME);
		if (Files.isRegularFile(dotenvPath)) {
			return dotenvPath;
		}
		Path examplePath = Path.of(DOTENV_EXAMPLE_FILE_NAME);
		if (Files.isRegularFile(examplePath)) {
			return examplePath;
		}
		return null;
	}

	private static void parseAndApply(String rawLine) {
		if (rawLine == null) {
			return;
		}

		String line = rawLine.trim();
		if (line.isEmpty() || line.startsWith("#")) {
			return;
		}
		if (line.startsWith("export ")) {
			line = line.substring("export ".length()).trim();
		}

		int equalsIndex = line.indexOf('=');
		if (equalsIndex <= 0) {
			return;
		}

		String key = line.substring(0, equalsIndex).trim();
		String value = line.substring(equalsIndex + 1).trim();
		if (key.isEmpty()) {
			return;
		}

		if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
			value = value.substring(1, value.length() - 1);
		}

		if (System.getProperty(key) == null && System.getenv(key) == null) {
			System.setProperty(key, value);
		}
	}
}
