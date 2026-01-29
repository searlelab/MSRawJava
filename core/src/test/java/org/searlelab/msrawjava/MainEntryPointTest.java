package org.searlelab.msrawjava;

import org.junit.jupiter.api.Test;

class MainEntryPointTest {

	@Test
	void mainHandlesHelpWithoutExit() throws Exception {
		new Main();
		Main.main(new String[] {"--help"});
	}
}
