package org.searlelab.msrawjava.gui.filebrowser;

import org.searlelab.msrawjava.io.VendorFile;
import org.searlelab.msrawjava.logging.Logger;

final class DirectorySummaryVendorFilter {
	static final String VENDOR_ALL="All";
	static final String VENDOR_ALL_RAW_INSTRUMENT_FILES="All raw instrument files";

	private DirectorySummaryVendorFilter() {
	}

	static String normalizeSavedVendorFilter(String saved) {
		if (saved==null||saved.isBlank()) {
			return VENDOR_ALL_RAW_INSTRUMENT_FILES;
		}
		if (VENDOR_ALL.equals(saved)||VENDOR_ALL_RAW_INSTRUMENT_FILES.equals(saved)) {
			return saved;
		}
		try {
			return VendorFile.valueOf(saved).name();
		} catch (IllegalArgumentException ignore) {
			Logger.errorException(ignore);
			return VENDOR_ALL_RAW_INSTRUMENT_FILES;
		}
	}

	static String getVendorFilterValueForSelection(Object selection) {
		if (selection instanceof VendorFile) {
			VendorFile vendor=(VendorFile)selection;
			return vendor.name();
		}
		if (selection instanceof String) {
			String value=(String)selection;
			return normalizeSavedVendorFilter(value);
		}
		return VENDOR_ALL_RAW_INSTRUMENT_FILES;
	}

	static boolean matchesVendorFilterValue(VendorFile vendor, String vendorFilterValue) {
		return matchesVendorFilterValue(vendor, vendorFilterValue, parseSpecificVendorFilter(vendorFilterValue));
	}

	static boolean matchesVendorFilterValue(VendorFile vendor, String vendorFilterValue, VendorFile specificVendorFilter) {
		if (vendor==null) return false;
		if (VENDOR_ALL.equals(vendorFilterValue)) return true;
		if (VENDOR_ALL_RAW_INSTRUMENT_FILES.equals(vendorFilterValue)) {
			return vendor==VendorFile.BRUKER||vendor==VendorFile.THERMO;
		}
		if (specificVendorFilter==null) return true;
		return vendor==specificVendorFilter;
	}

	static VendorFile parseSpecificVendorFilter(String vendorFilterValue) {
		if (vendorFilterValue==null||vendorFilterValue.isBlank()) return null;
		if (VENDOR_ALL.equals(vendorFilterValue)||VENDOR_ALL_RAW_INSTRUMENT_FILES.equals(vendorFilterValue)) return null;
		try {
			return VendorFile.valueOf(vendorFilterValue);
		} catch (IllegalArgumentException ignore) {
			Logger.errorException(ignore);
			return null;
		}
	}
}
