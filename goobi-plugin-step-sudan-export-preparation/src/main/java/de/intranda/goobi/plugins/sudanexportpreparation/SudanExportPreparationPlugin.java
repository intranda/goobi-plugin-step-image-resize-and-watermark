package de.intranda.goobi.plugins.sudanexportpreparation;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.configuration.reloading.FileChangedReloadingStrategy;
import org.apache.commons.configuration.tree.xpath.XPathExpressionEngine;
import org.goobi.beans.LogEntry;
import org.goobi.beans.Step;
import org.goobi.production.enums.LogType;
import org.goobi.production.enums.PluginGuiType;
import org.goobi.production.enums.PluginReturnValue;
import org.goobi.production.enums.PluginType;
import org.goobi.production.enums.StepReturnValue;
import org.goobi.production.plugin.interfaces.IStepPluginVersion2;

import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.helper.ShellScript;
import de.sub.goobi.helper.ShellScriptReturnValue;
import de.sub.goobi.helper.exceptions.DAOException;
import de.sub.goobi.helper.exceptions.SwapException;
import de.sub.goobi.persistence.managers.ProcessManager;
import lombok.extern.log4j.Log4j2;
import net.xeoh.plugins.base.annotations.PluginImplementation;
import ugh.dl.DigitalDocument;
import ugh.dl.DocStruct;
import ugh.dl.Metadata;
import ugh.dl.MetadataType;
import ugh.dl.Prefs;
import ugh.exceptions.PreferencesException;
import ugh.exceptions.ReadException;
import ugh.exceptions.WriteException;

@PluginImplementation
@Log4j2
public class SudanExportPreparationPlugin implements IStepPluginVersion2 {

    private static String TITLE = "intranda_step_sudan-export-preparation";
    private Step step;
    private XMLConfiguration pluginConfig;
    private SubnodeConfiguration projectAndStepConfig;

    @Override
    public void initialize(Step step, String returnPath) {
        this.step = step;
        String projectName = step.getProzess().getProjekt().getTitel();
        pluginConfig = ConfigPlugins.getPluginConfig(TITLE);
        pluginConfig.setExpressionEngine(new XPathExpressionEngine());
        pluginConfig.setReloadingStrategy(new FileChangedReloadingStrategy());

        // order of configuration is:
        // 1.) project name and step name matches
        // 2.) step name matches and project is *
        // 3.) project name matches and step name is *
        // 4.) project name and step name are *
        try {
            projectAndStepConfig = pluginConfig.configurationAt("//config[./project = '" + projectName + "'][./step = '" + step.getTitel() + "']");
        } catch (IllegalArgumentException e) {
            try {
                projectAndStepConfig = pluginConfig.configurationAt("//config[./project = '*'][./step = '" + step.getTitel() + "']");
            } catch (IllegalArgumentException e1) {
                try {
                    projectAndStepConfig = pluginConfig.configurationAt("//config[./project = '" + projectName + "'][./step = '*']");
                } catch (IllegalArgumentException e2) {
                    projectAndStepConfig = pluginConfig.configurationAt("//config[./project = '*'][./step = '*']");
                }
            }
        }
    }

    @Override
    public boolean execute() {
        PluginReturnValue ret = run();
        return ret != PluginReturnValue.ERROR;
    }

    @Override
    public String cancel() {
        return null;
    }

    @Override
    public String finish() {
        return null;
    }

    @Override
    public HashMap<String, StepReturnValue> validate() {
        return null;
    }

    @Override
    public Step getStep() {
        return step;
    }

    @Override
    public PluginGuiType getPluginGuiType() {
        return PluginGuiType.NONE;
    }

    @Override
    public String getPagePath() {
        return null;
    }

    @Override
    public PluginType getType() {
        return PluginType.Step;
    }

    @Override
    public String getTitle() {
        return TITLE;
    }

    @Override
    public PluginReturnValue run() {
        boolean resizeOK = resizeImages();
        if (!resizeOK) {
            return PluginReturnValue.ERROR;
        }
        boolean waterMarksOK = addWatermarks();
        if (!waterMarksOK) {
            return PluginReturnValue.ERROR;
        }
        return PluginReturnValue.FINISH;
    }

    private boolean resizeImages() {
        String gmPath = pluginConfig.getString("gmPath", "/usr/bin/gm");

        org.goobi.beans.Process process = step.getProzess();

        String sourceDir = null;
        String destDir = null;
        try {
            sourceDir = process.getConfiguredImageFolder(projectAndStepConfig.getString("sourceDir", "cropped"));
            destDir = process.getConfiguredImageFolder(projectAndStepConfig.getString("destDir", "media"));
        } catch (IOException | InterruptedException | SwapException | DAOException e2) {
            writeErrorToProcessLog("Error reading configured input and output folders");
            log.error(e2);
            return false;
        }

        int size = 1500;
        try {
            size = getResizeSize();
        } catch (PreferencesException | ReadException | WriteException | IOException | InterruptedException | SwapException | DAOException e3) {
            writeErrorToProcessLog("Error reading metadata to determine resizing size.");
            log.error(e3);
            return false;
        }
        if (size == 0) {
            writeErrorToProcessLog("There is no image size configured for this process. Please check the plugin configuration.");
            return false;
        }
        String convertSize = String.format("%dx%d>", size, size);
        Path destDirPath = Paths.get(destDir);
        try {
            Files.createDirectories(destDirPath);
        } catch (IOException e2) {
        }

        try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(Paths.get(sourceDir))) {
            for (Path p : dirStream) {
                String inputAbsolutePath = p.toAbsolutePath().toString();
                String outputAbsolutePath = destDirPath.resolve(p.getFileName()).toAbsolutePath().toString();
                try {
                    ShellScriptReturnValue result = ShellScript.callShell(
                            Arrays.asList(gmPath, "convert", inputAbsolutePath, "-resize", convertSize, outputAbsolutePath),
                            this.step.getProcessId());
                    if (result.getReturnCode() != 0) {
                        writeErrorToProcessLog("Error converting image. Command output:\n" + result.getErrorText());
                        return false;
                    }
                } catch (InterruptedException e) {
                    log.error(e);
                }
            }
        } catch (IOException e1) {
            log.error(e1);
            writeErrorToProcessLog("Error converting images");
            return false;
        }
        return true;
    }

    private int getResizeSize()
            throws PreferencesException, ReadException, WriteException, IOException, InterruptedException, SwapException, DAOException {
        DigitalDocument digDoc = step.getProzess().readMetadataFile().getDigitalDocument();
        Prefs prefs = step.getProzess().getRegelsatz().getPreferences();
        String collectionName = getMedatataValue("singleDigCollection", digDoc, prefs);
        String mediaType = getMedatataValue("Type", digDoc, prefs);

        List<HierarchicalConfiguration> imageConfigs = findImageConfigs(collectionName, mediaType, projectAndStepConfig);
        if (imageConfigs.isEmpty()) {
            return 0;
        }

        return imageConfigs.get(0).getInt("./resizeTo", 0);
    }

    private void writeErrorToProcessLog(String content) {
        LogEntry le = LogEntry.build(step.getProcessId())
                .withCreationDate(new Date())
                .withContent(content)
                .withType(LogType.ERROR)
                .withUsername("automatic");
        ProcessManager.saveLogEntry(le);
    }

    private boolean addWatermarks() {
        String convertPath = pluginConfig.getString("convertPath", "/usr/bin/convert");
        //first, find which (if any) watermark we want to render
        List<WatermarkDescription> watermarkDescriptions = new ArrayList<WatermarkDescription>();
        try {
            watermarkDescriptions = findWatermarkDescriptions();
        } catch (PreferencesException | ReadException | WriteException | IOException | InterruptedException | SwapException | DAOException e1) {
            writeErrorToProcessLog("Error reading metadata from process.");
            log.error(e1);
            return false;
        }
        if (watermarkDescriptions.isEmpty()) {
            LogEntry le = LogEntry.build(step.getProcessId())
                    .withCreationDate(new Date())
                    .withContent("Could not find any watermark configuration for this process - not watermarking")
                    .withType(LogType.INFO)
                    .withUsername("automatic");
            ProcessManager.saveLogEntry(le);
            return true;
        }
        boolean preRenderOK = preRenderWatermarkImages(convertPath, watermarkDescriptions);
        if (!preRenderOK) {
            return false;
        }
        org.goobi.beans.Process process = step.getProzess();
        String destDir = null;
        try {
            destDir = process.getConfiguredImageFolder(projectAndStepConfig.getString("destDir", "media"));
        } catch (IOException | InterruptedException | SwapException | DAOException e) {
            writeErrorToProcessLog("Error reading configured input and output folders");
            log.error(e);
            return false;
        }
        try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(Paths.get(destDir))) {
            //for each image, composite image with watermarks
            for (Path canvasImage : dirStream) {
                for (WatermarkDescription watermarkDescription : watermarkDescriptions) {
                    renderWatermarkToImage(canvasImage, watermarkDescription);
                }
            }
        } catch (IOException e1) {
            writeErrorToProcessLog("Error listing source directory for watermarking.");
            log.error(e1);
            return false;
        } finally {
            cleanupTempWatermarkImages(watermarkDescriptions);
        }

        return true;
    }

    private boolean renderWatermarkToImage(Path canvasImage, WatermarkDescription wd) {
        //gm composite -dissolve 50% -geometry +550+400 -gravity southeast WATERMARK_FILE.png canvas.tif result.tif
        String gmPath = pluginConfig.getString("gmPath", "/usr/bin/gm");
        List<String> command = Arrays.asList(gmPath, "composite", "-dissolve", "50%", "-geometry",
                String.format("+%d+%d", wd.getXDistance(), wd.getYDistance()), "-gravity",
                wd.getLocation(), wd.getImagePath().toAbsolutePath().toString(), canvasImage.toAbsolutePath().toString(),
                canvasImage.toAbsolutePath().toString());
        try {
            ShellScriptReturnValue ret = ShellScript.callShell(command, step.getProcessId());
            if (ret.getReturnCode() != 0) {
                writeErrorToProcessLog("Error watermarking image. Process output was:\n" + ret.getErrorText());
                return false;
            }
        } catch (IOException | InterruptedException e) {
            writeErrorToProcessLog("Error watermarking image.");
            log.error(e);
            return false;
        }
        return true;
    }

    private void cleanupTempWatermarkImages(List<WatermarkDescription> watermarkDescriptions) {
        for (WatermarkDescription watermarkDescription : watermarkDescriptions) {
            if (!watermarkDescription.isImage()) {
                try {
                    Files.deleteIfExists(watermarkDescription.getImagePath());
                } catch (IOException e) {
                    log.error("could not delete temp file");
                }
            }
        }
    }

    private boolean preRenderWatermarkImages(String convertPath, List<WatermarkDescription> watermarkDescriptions) {
        for (WatermarkDescription watermarkDescription : watermarkDescriptions) {
            if (!watermarkDescription.isImage()) {
                try {
                    Path watermarkImagePath = renderWatermarkText(watermarkDescription, convertPath);
                    watermarkDescription.setImagePath(watermarkImagePath);
                } catch (IOException | InterruptedException e) {
                    writeErrorToProcessLog("Error creating watermark image from text");
                    log.error(e);

                    return false;
                }
            }
        }
        return true;
    }

    private Path renderWatermarkText(WatermarkDescription watermarkDescription, String convertPath) throws IOException, InterruptedException {
        // command to create watermark-file when text: 
        //convert -size 450x200 -background none -font Arial -fill white -gravity center caption:"Goobi.io" -shade 240x40 WATERMARK_FILE.png
        String tempDir = System.getProperty("java.io.tmpdir");
        Path watermarkFile = Paths.get(tempDir, "watermark_" + UUID.randomUUID().toString() + ".png");
        ShellScript.callShell(Arrays.asList(convertPath, "-size", "450x200", "-background", "none", "-font", "Arial",
                "-fill", "white", "-gravity", "center", String.format("caption:\"%s\"", watermarkDescription.getText()),
                "-shade", "240x40", watermarkFile.toAbsolutePath().toString()), step.getProcessId());
        return watermarkFile;
    }

    private List<WatermarkDescription> findWatermarkDescriptions()
            throws PreferencesException, ReadException, WriteException, IOException, InterruptedException, SwapException, DAOException {
        DigitalDocument digDoc = step.getProzess().readMetadataFile().getDigitalDocument();
        Prefs prefs = step.getProzess().getRegelsatz().getPreferences();
        String collectionName = getMedatataValue("singleDigCollection", digDoc, prefs);
        String mediaType = getMedatataValue("Type", digDoc, prefs);

        return findWatermarkDescriptions(collectionName, mediaType, this.projectAndStepConfig);
    }

    private String getMedatataValue(String type, DigitalDocument digDoc, Prefs prefs) {
        MetadataType mdType = prefs.getMetadataTypeByName(type);
        DocStruct topStruct = digDoc.getLogicalDocStruct();
        if (topStruct.getType().isAnchor()) {
            topStruct = topStruct.getAllChildren().get(0);
        }
        @SuppressWarnings("unchecked")
        List<Metadata> metaList = (List<Metadata>) topStruct.getAllMetadataByType(mdType);
        if (metaList != null && !metaList.isEmpty()) {
            return metaList.get(0).getValue();
        }
        return null;
    }

    public static List<WatermarkDescription> findWatermarkDescriptions(String wantedCollectionName, String wantedMediaType,
            SubnodeConfiguration config) {
        List<WatermarkDescription> descriptions = new ArrayList<WatermarkDescription>();
        List<HierarchicalConfiguration> imageConfigs = findImageConfigs(wantedCollectionName, wantedMediaType, config);
        for (HierarchicalConfiguration imageConfig : imageConfigs) {
            for (HierarchicalConfiguration watermarkConfig : imageConfig.configurationsAt("./watermark")) {
                descriptions.add(descriptionFromConfig(watermarkConfig));
            }
        }
        return descriptions;
    }

    private static List<HierarchicalConfiguration> findImageConfigs(String wantedCollectionName, String wantedMediaType,
            SubnodeConfiguration config) {
        List<HierarchicalConfiguration> allImageConfigs = config.configurationsAt("./imageConfig");
        if (allImageConfigs == null) {
            return new ArrayList<HierarchicalConfiguration>();
        }
        List<HierarchicalConfiguration> filteredImageConfigs = new ArrayList<>();
        for (HierarchicalConfiguration imageConfig : allImageConfigs) {
            if (imageConfig.getString("@collection", "").equals("*") || imageConfig.getString("@collection", "").equals(wantedCollectionName)) {
                String confMediaType = imageConfig.getString("@mediaType", "");
                if (confMediaType.equals("*") || confMediaType.equals(wantedMediaType)) {
                    filteredImageConfigs.add(imageConfig);
                }
            }
        }
        return filteredImageConfigs;
    }

    private static WatermarkDescription descriptionFromConfig(HierarchicalConfiguration watermarkConfig) {
        String imageLocation = watermarkConfig.getString("image");
        Path imagePath = imageLocation == null ? null : Paths.get(imageLocation);
        String text = watermarkConfig.getString("text");
        String location = watermarkConfig.getString("location", "southeast");
        int xDistance = watermarkConfig.getInt("xDistance", 100);
        int yDistance = watermarkConfig.getInt("yDistance", 100);

        return new WatermarkDescription(imagePath != null, imagePath, text, location, xDistance, yDistance);
    }

    @Override
    public int getInterfaceVersion() {
        return 0;
    }

}
