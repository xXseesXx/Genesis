package genesis.core.fields;

public final class FieldId<T> {
    public final String name, label, units;
    public final Class<T> type;
    public final double displayMin, displayMax;

    public FieldId(String name, String label, String units, Class<T> type, double min, double max) {
        this.name = name; this.label = label; this.units = units; this.type = type;
        this.displayMin = min; this.displayMax = max;
    }
}
