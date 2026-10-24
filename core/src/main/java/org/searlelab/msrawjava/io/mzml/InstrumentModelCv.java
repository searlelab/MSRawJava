package org.searlelab.msrawjava.io.mzml;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Resolves vendor model names to canonical PSI-MS instrument terms for mzML output. */
public final class InstrumentModelCv {

	public static final String CV_VERSION="4.2.8";
	private static final Map<String, ModelTerm> MODELS=loadModels();

	private InstrumentModelCv() {
	}

	/** Matches complete model names; unknown variants must not be assigned a different model's accession. */
	public static Optional<InstrumentId> find(String name) {
		ModelTerm term=name==null?null:MODELS.get(normalize(name));
		return term==null?Optional.empty():Optional.of(term.model);
	}

	/** Uses OBO vendor ancestry rather than inferring the manufacturer from model keywords. */
	public static boolean isThermo(String name) {
		ModelTerm term=name==null?null:MODELS.get(normalize(name));
		return term!=null&&term.thermo;
	}

	/** Uses the same preferred metadata keys for mzML writing and legacy metadata recovery. */
	public static Optional<String> modelName(Map<String, String> metadata) {
		for (String key : new String[] {"meta.InstrumentName", "instrument.model", "instrument.name"}) {
			String name=metadata.get(key);
			if (name!=null&&!name.isBlank()) return Optional.of(name.trim());
		}
		return Optional.empty();
	}

	/** Recognizes Thermo metadata even when the vendor or model name is missing. */
	public static boolean isThermoMetadata(Map<String, String> metadata) {
		String vendor=metadata.get("meta.InstrumentVendor");
		return (vendor!=null&&vendor.toLowerCase(Locale.ROOT).contains("thermo"))||modelName(metadata).map(InstrumentModelCv::isThermo).orElse(false)
				||metadata.keySet().stream().anyMatch(key -> key.startsWith("thermo."));
	}

	/** Vendor and series terms are placeholders that can be refined by a saved model name. */
	public static boolean isGenericModel(String name) {
		if (name==null||name.isBlank()||"instrument model".equalsIgnoreCase(name.trim())) return true;
		ModelTerm term=MODELS.get(normalize(name));
		return term!=null&&term.generic;
	}

	private static Map<String, ModelTerm> loadModels() {
		Map<String, ModelTerm> models=new HashMap<>();
		try (BufferedReader reader=new BufferedReader(new InputStreamReader(Objects.requireNonNull(
				InstrumentModelCv.class.getResourceAsStream("instrument-models.tsv"), "Missing bundled instrument-models.tsv"), StandardCharsets.UTF_8))) {
			String line;
			while ((line=reader.readLine())!=null) {
				if (line.isBlank()||line.startsWith("#")) continue;
				String[] fields=line.split("\t");
				if (fields.length<4||!("family".equals(fields[3])||"model".equals(fields[3]))) throw new IOException("Invalid instrument catalog row: "+line);
				ModelTerm model=new ModelTerm(InstrumentId.builder().setAccession(fields[0]).setName(fields[1]).build(), "Thermo".equals(fields[2]),
						"family".equals(fields[3]));
				addName(models, fields[1], model);
				for (int i=4; i<fields.length; i++) {
					addName(models, fields[i], model);
				}
			}
		} catch (IOException e) {
			throw new ExceptionInInitializerError(e);
		}
		// Canonical names take precedence: "Velos Pro" and "Orbitrap Velos Pro" are distinct CV terms.
		Map<String, ModelTerm> aliases=new HashMap<>(models);
		for (Map.Entry<String, ModelTerm> entry : models.entrySet()) {
			if (entry.getKey().startsWith("orbitrap ")) {
				String shortName=entry.getKey().substring("orbitrap ".length());
				if (!models.containsKey(shortName)) putName(aliases, shortName, entry.getValue());
			}
		}
		return Collections.unmodifiableMap(aliases);
	}

	private static void addName(Map<String, ModelTerm> models, String name, ModelTerm model) {
		String normalized=normalize(name);
		putName(models, normalized, model);
	}

	private static void putName(Map<String, ModelTerm> models, String name, ModelTerm model) {
		ModelTerm previous=models.putIfAbsent(name, model);
		if (previous!=null&&!previous.model.equals(model.model)) throw new IllegalStateException("Ambiguous instrument model alias: "+name);
	}

	private static String normalize(String name) {
		String normalized=name.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
		for (String prefix : new String[] {"thermo fisher scientific ", "thermo scientific ", "thermo electron ", "thermo finnigan ",
				"thermo ", "bruker daltonics ", "bruker "}) {
			if (normalized.startsWith(prefix)) {
				String remainder=normalized.substring(prefix.length());
				return "instrument model".equals(remainder)?normalized:remainder;
			}
		}
		return normalized;
	}

	private static final class ModelTerm {
		final InstrumentId model;
		final boolean thermo;
		final boolean generic;

		ModelTerm(InstrumentId model, boolean thermo, boolean generic) {
			this.model=model;
			this.thermo=thermo;
			this.generic=generic;
		}
	}
}
