package io.github.halftermeyer.rdfsnapshot.model;

import java.util.Objects;

/** A Cypher {@code POINT}. Coordinates are x/y(/z), i.e. longitude/latitude(/height) for WGS-84. */
public record LpgPoint(Crs crs, double[] coordinates) {

    /** The four coordinate reference systems of Neo4j. */
    public enum Crs {
        WGS84_2D(4326, 2),
        WGS84_3D(4979, 3),
        CARTESIAN_2D(7203, 2),
        CARTESIAN_3D(9157, 3);

        public final int srid;
        public final int dimension;

        Crs(int srid, int dimension) {
            this.srid = srid;
            this.dimension = dimension;
        }

        public static Crs fromSrid(int srid) {
            for (Crs crs : values()) {
                if (crs.srid == srid) {
                    return crs;
                }
            }
            throw new IllegalArgumentException("Unknown point SRID: " + srid);
        }
    }

    public LpgPoint {
        Objects.requireNonNull(crs, "crs");
        if (coordinates.length != crs.dimension) {
            throw new IllegalArgumentException(
                    crs + " needs " + crs.dimension + " coordinates, got " + coordinates.length);
        }
        coordinates = coordinates.clone();
    }

    @Override
    public double[] coordinates() {
        return coordinates.clone();
    }
}
