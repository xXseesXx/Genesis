package genesis.harness;

import genesis.core.fields.RockColumn;

/** Structured field transport, separate from numeric raster metadata. */
public final class GeologyJson {
    private GeologyJson() {}
    private static String bound(Long value) { return value==null?"null":"\""+value+"\""; }
    public static String column(RockColumn column) {
        var out=new StringBuilder("{\"surfaceY\":\"").append(column.surfaceY).append("\",\"layers\":[");
        boolean comma=false;
        for(var layer:column.layers()) {
            if(comma)out.append(',');comma=true;
            out.append("{\"rock\":\"").append(layer.rock.label).append("\",\"rockId\":").append(layer.rock.id)
                .append(",\"lower\":").append(bound(layer.lower)).append(",\"upper\":").append(bound(layer.upper))
                .append(",\"formationAge\":").append(layer.formationAge).append(",\"atSurface\":").append(layer.contains(column.surfaceY)).append('}');
        }
        return out.append("]}").toString();
    }
}
