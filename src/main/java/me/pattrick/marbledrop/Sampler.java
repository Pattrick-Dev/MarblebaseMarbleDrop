package me.pattrick.marbledrop;

import java.io.IOException;
import java.util.stream.Stream;
import java.nio.file.Path;
import java.util.Arrays;
import java.nio.file.Files;
import java.io.File;
import org.bukkit.Bukkit;
import java.util.ArrayList;

public class Sampler {
    public static ArrayList<String> main() throws IOException {
        final File dataFolder = Bukkit.getPluginManager().getPlugin("MarbleBaseMD").getDataFolder();
        final File filePath = new File(dataFolder, "heads.yml");
        final Path filePaths = filePath.toPath();
        final Stream<String> linesStream = Files.lines(filePaths);
        long lines;
        lines = linesStream.count();
		if (linesStream != null) {
		    linesStream.close();
		}
        final long randomLine = (long) (Math.random() * (lines - 1L));
        final String randomHeadString = Files.readAllLines(filePaths).get((int)randomLine);
        Bukkit.getConsoleSender().sendMessage("---------------------------------------- " + randomHeadString);
        final String[] rhsSplit = randomHeadString.split("-");
        final ArrayList<String> headContentList = new ArrayList<String>(Arrays.asList(rhsSplit));
        return headContentList;
    }
}