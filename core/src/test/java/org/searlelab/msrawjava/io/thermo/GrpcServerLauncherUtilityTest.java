package org.searlelab.msrawjava.io.thermo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GrpcServerLauncherUtilityTest {

	private String originalOsName;
	private String originalOsArch;

	@TempDir
	Path tmp;

	@BeforeEach
	void setUp() {
		originalOsName=System.getProperty("os.name");
		originalOsArch=System.getProperty("os.arch");
	}

	@AfterEach
	void tearDown() {
		System.setProperty("os.name", originalOsName);
		System.setProperty("os.arch", originalOsArch);
	}

	@Test
	void ridResolvesForCommonPlatforms() {
		System.setProperty("os.name", "Windows 11");
		System.setProperty("os.arch", "amd64");
		assertTrue(GrpcServerLauncher.isWin());
		assertFalse(GrpcServerLauncher.isMac());
		assertEquals("win-x64", GrpcServerLauncher.rid());

		System.setProperty("os.name", "Mac OS X");
		System.setProperty("os.arch", "aarch64");
		assertTrue(GrpcServerLauncher.isMac());
		assertTrue(GrpcServerLauncher.isArm64());
		assertEquals("osx-x64", GrpcServerLauncher.rid());

		System.setProperty("os.name", "Linux");
		System.setProperty("os.arch", "x86_64");
		assertFalse(GrpcServerLauncher.isWin());
		assertFalse(GrpcServerLauncher.isMac());
		assertFalse(GrpcServerLauncher.isArm64());
		assertEquals("linux-x64", GrpcServerLauncher.rid());
	}

	@Test
	void closeHandlesNullsAndDeletesWorkDir() throws Exception {
		Path workDir=tmp.resolve("launcher-work");
		Files.createDirectories(workDir);
		Files.writeString(workDir.resolve("file.txt"), "data");

		GrpcServerLauncher launcher=allocateLauncher(0, null, workDir);
		launcher.close();

		assertFalse(Files.exists(workDir));
	}

	private static GrpcServerLauncher allocateLauncher(int port, Process proc, Path workDir) throws Exception {
		return GrpcServerLauncher.forTest(port, proc, workDir);
	}

	private static void setField(Object target, String name, Object value) throws Exception {
		Field field=target.getClass().getDeclaredField(name);
		field.setAccessible(true);
		field.set(target, value);
	}
}
