package de.intranda.goobi.plugins.imageresize;

import java.nio.file.Path;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class WatermarkDescription {
    private boolean image;
    private Path imagePath;
    private String text;
    private String location; //imagemagick gravity: north, northeast, east, southeast...
    private int xDistance;
    private int yDistance;
}
