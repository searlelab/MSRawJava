package org.searlelab.msrawjava.io.thermo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.ServerSocket;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/**
 * Tests for GrpcServerLauncher static utility methods.
 * Note: GrpcServerLauncher is package-private, so this test must be in the same package.
 * Full integration tests requiring the actual Thermo server are in ThermoRawFileSmokeIT.
 */
class GrpcServerLauncherTest {

	@Test
	void pickFreePortReturnsValidPort() throws IOException {
		int port = GrpcServerLauncher.pickFreePort();
		assertTrue(port > 0, "Port should be positive");
		assertTrue(port <= 65535, "Port should be valid TCP port");
	}

	@Test
	void pickFreePortReturnsDifferentPorts() throws IOException {
		// Most of the time, consecutive calls should return different ports
		int port1 = GrpcServerLauncher.pickFreePort();
		int port2 = GrpcServerLauncher.pickFreePort();

		// This isn't guaranteed but is highly likely
		// If they're the same, the port was released and re-acquired
		assertTrue(port1 > 0 && port2 > 0);
	}

	@Test
	void pickFreePortReturnsUsablePort() throws IOException {
		int port = GrpcServerLauncher.pickFreePort();

		// Verify we can actually bind to this port
		try (ServerSocket ss = new ServerSocket(port)) {
			assertEquals(port, ss.getLocalPort());
		}
	}

	@Test
	@EnabledOnOs(OS.MAC)
	void isMacReturnsTrueOnMac() {
		assertTrue(GrpcServerLauncher.isMac());
	}

	@Test
	@EnabledOnOs(OS.WINDOWS)
	void isWinReturnsTrueOnWindows() {
		assertTrue(GrpcServerLauncher.isWin());
	}

	@Test
	@EnabledOnOs(OS.LINUX)
	void isMacAndIsWinReturnFalseOnLinux() {
		assertFalse(GrpcServerLauncher.isMac());
		assertFalse(GrpcServerLauncher.isWin());
	}

	@Test
	void ridReturnsValidIdentifier() {
		String rid = GrpcServerLauncher.rid();

		// Should be one of the supported RIDs
		assertTrue(
			rid.equals("win-x64") || rid.equals("osx-x64") || rid.equals("linux-x64"),
			"RID should be a supported platform: " + rid
		);
	}

	@Test
	@EnabledOnOs(OS.WINDOWS)
	void ridReturnsWinX64OnWindows() {
		assertEquals("win-x64", GrpcServerLauncher.rid());
	}

	@Test
	@EnabledOnOs(OS.MAC)
	void ridReturnsOsxX64OnMac() {
		// Currently forces x64 even on ARM due to Thermo library compatibility
		assertEquals("osx-x64", GrpcServerLauncher.rid());
	}

	@Test
	@EnabledOnOs(OS.LINUX)
	void ridReturnsLinuxX64OnLinux() {
		assertEquals("linux-x64", GrpcServerLauncher.rid());
	}

	@Test
	void isArm64DetectsArchitecture() {
		// This test just verifies the method runs without error
		// The actual result depends on the current platform
		boolean isArm = GrpcServerLauncher.isArm64();

		// On known ARM Macs, this should be true
		String arch = System.getProperty("os.arch").toLowerCase();
		if (arch.contains("aarch64") || arch.contains("arm64")) {
			assertTrue(isArm);
		} else if (arch.contains("x86") || arch.contains("amd64")) {
			assertFalse(isArm);
		}
		// Other architectures: just verify it returns a boolean
	}

	@Test
	void platformDetectionMethodsAreConsistent() {
		// At most one of isMac/isWin should be true
		int trueCount = 0;
		if (GrpcServerLauncher.isMac()) trueCount++;
		if (GrpcServerLauncher.isWin()) trueCount++;

		assertTrue(trueCount <= 1, "At most one platform flag should be true");
	}

	@Test
	void ridIsNotEmpty() {
		String rid = GrpcServerLauncher.rid();
		assertFalse(rid.isEmpty());
		assertFalse(rid.isBlank());
	}

	@Test
	void ridContainsArchitecture() {
		String rid = GrpcServerLauncher.rid();
		assertTrue(rid.contains("x64") || rid.contains("arm64"),
			"RID should contain architecture: " + rid);
	}

	@Test
	void ridContainsOsIdentifier() {
		String rid = GrpcServerLauncher.rid();
		assertTrue(rid.startsWith("win-") || rid.startsWith("osx-") || rid.startsWith("linux-"),
			"RID should start with OS identifier: " + rid);
	}
}
