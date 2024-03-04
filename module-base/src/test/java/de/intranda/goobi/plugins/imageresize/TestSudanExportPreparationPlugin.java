package de.intranda.goobi.plugins.imageresize;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.configuration.tree.xpath.XPathExpressionEngine;
import org.junit.Test;

public class TestSudanExportPreparationPlugin {
    @Test
    public void testFindWatermarkDescriptions() throws ConfigurationException, IOException {
        XMLConfiguration xmlConfig = readConfig();
        SubnodeConfiguration config = xmlConfig.configurationAt("//config[./project = '*'][./step = '*']");
        List<WatermarkDescription> shouldBeEmpty = ImageResizeAndWatermarkPlugin.findWatermarkDescriptions("", "", config);
        assertTrue("list for no collection/media type should be empty", shouldBeEmpty.isEmpty());

        List<WatermarkDescription> shouldNotBeEmpty = ImageResizeAndWatermarkPlugin.findWatermarkDescriptions("mycollection", "", config);
        assertTrue("list for for 'mycollection' should not be empty", !shouldNotBeEmpty.isEmpty());
    }

    private XMLConfiguration readConfig() throws ConfigurationException {
        File configFile = new File("src/test/resources/testconfig.xml");
        XMLConfiguration xmlConfig = new XMLConfiguration(configFile);
        xmlConfig.setExpressionEngine(new XPathExpressionEngine());
        return xmlConfig;
    }
}
