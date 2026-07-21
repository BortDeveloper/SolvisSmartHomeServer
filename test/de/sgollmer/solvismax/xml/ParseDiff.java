package de.sgollmer.solvismax.xml;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Kanonische, deterministische Serialisierung eines Objektgraphen über seine
 * öffentlichen Getter — das Werkzeug hinter dem <b>Dual-Parse-Differenztest</b>.
 *
 * <p>
 * Idee: Parst man dieselbe Konfiguration mit zwei Parsern (der bisherigen
 * {@code XMLLibrary} und dem neuen JAXB-Parser), müssen die kanonischen Formen
 * der Ergebnisgraphen <b>zeichengleich</b> sein. Weil beide Parser während der
 * Umstellung denselben Domänentyp ({@code BaseData} usw.) liefern, erfasst der
 * Vergleich automatisch <em>jedes</em> über einen Getter erreichbare Feld — kein
 * blinder Fleck durch vergessene Einzel-Assertions.
 * </p>
 *
 * <p>
 * Verhalten: JDK-Werte (String/Zahl/Boolean/Enum) werden per {@code toString}
 * abgebildet; Collections/Arrays elementweise (Reihenfolge bleibt erhalten —
 * die soll ein Parser reproduzieren); Maps schlüsselsortiert; Projekt-Objekte
 * (Paket {@code de.sgollmer.solvismax}) rekursiv über ihre nach Namen sortierten
 * Getter. Zyklen werden über eine Identitäts-Menge erkannt, die Tiefe ist
 * begrenzt; Getter, die werfen, werden als {@code <err>} notiert (in beiden
 * Läufen gleich, daher für den Vergleich unschädlich).
 * </p>
 */
final class ParseDiff {

	private static final String PROJECT_PACKAGE = "de.sgollmer.solvismax";
	private static final int MAX_DEPTH = 40;

	private ParseDiff() {
	}

	/** Deterministische kanonische Form des Graphen ab {@code root}. */
	static String canonical(final Object root) {
		final StringBuilder sb = new StringBuilder(4096);
		append(sb, root, new IdentityHashMap<>(), 0);
		return sb.toString();
	}

	private static void append(final StringBuilder sb, final Object value,
			final IdentityHashMap<Object, Boolean> onPath, final int depth) {
		if (value == null) {
			sb.append("null");
			return;
		}
		final Class<?> type = value.getClass();

		if (type.isEnum() || value instanceof CharSequence || value instanceof Number
				|| value instanceof Boolean || value instanceof Character) {
			sb.append(value);
			return;
		}
		if (value instanceof Collection<?>) {
			sb.append('[');
			for (final Object element : (Collection<?>) value) {
				append(sb, element, onPath, depth + 1);
				sb.append(',');
			}
			sb.append(']');
			return;
		}
		if (type.isArray()) {
			sb.append('[');
			final int n = Array.getLength(value);
			for (int i = 0; i < n; i++) {
				append(sb, Array.get(value, i), onPath, depth + 1);
				sb.append(',');
			}
			sb.append(']');
			return;
		}
		if (value instanceof Map<?, ?>) {
			final TreeMap<String, Object> sorted = new TreeMap<>();
			for (final Map.Entry<?, ?> e : ((Map<?, ?>) value).entrySet()) {
				sorted.put(String.valueOf(e.getKey()), e.getValue());
			}
			sb.append('{');
			for (final Map.Entry<String, Object> e : sorted.entrySet()) {
				sb.append(e.getKey()).append('=');
				append(sb, e.getValue(), onPath, depth + 1);
				sb.append(',');
			}
			sb.append('}');
			return;
		}

		// Fremde (Nicht-Projekt-)Objekte nicht aufklappen — nur den Typnamen
		// notieren (verhindert das Eintauchen in JDK-/Bibliotheks-Interna).
		if (!type.getName().startsWith(PROJECT_PACKAGE)) {
			sb.append(type.getSimpleName());
			return;
		}

		// Zyklus- und Tiefenschutz.
		if (onPath.put(value, Boolean.TRUE) != null || depth > MAX_DEPTH) {
			sb.append('@').append(type.getSimpleName());
			return;
		}

		sb.append(type.getSimpleName()).append('{');
		final TreeMap<String, Method> getters = new TreeMap<>();
		for (final Method m : type.getMethods()) {
			if (m.getParameterCount() != 0 || m.getReturnType() == void.class) {
				continue;
			}
			final String name = m.getName();
			if ("getClass".equals(name)) {
				continue;
			}
			if (name.startsWith("get") || name.startsWith("is")) {
				getters.put(name, m);
			}
		}
		for (final Map.Entry<String, Method> e : getters.entrySet()) {
			sb.append(e.getKey()).append('=');
			Object result;
			try {
				result = e.getValue().invoke(value);
			} catch (final Throwable t) {
				result = "<err>";
			}
			append(sb, result, onPath, depth + 1);
			sb.append(';');
		}
		sb.append('}');
		// Nach Verlassen des Pfades wieder freigeben -> geteilte Objekte (DAG)
		// dürfen in anderen Zweigen erneut vollständig erscheinen.
		onPath.remove(value);
	}
}
