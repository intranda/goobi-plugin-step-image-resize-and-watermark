package de.intranda.goobi.plugins.sudanexportpreparation;

import java.util.HashMap;

import org.goobi.beans.Step;
import org.goobi.production.enums.PluginGuiType;
import org.goobi.production.enums.PluginReturnValue;
import org.goobi.production.enums.PluginType;
import org.goobi.production.enums.StepReturnValue;
import org.goobi.production.plugin.interfaces.IStepPluginVersion2;

import lombok.extern.log4j.Log4j;
import net.xeoh.plugins.base.annotations.PluginImplementation;

@PluginImplementation
@Log4j
public class SudanExportPreparationPlugin implements IStepPluginVersion2 {

    private static String TITLE = "intranda_step_sudan-export-preparation";
    private Step step;

    @Override
    public void initialize(Step step, String returnPath) {
        // TODO Auto-generated method stub
        this.step = step;
    }

    @Override
    public boolean execute() {
        PluginReturnValue ret = run();
        return ret != PluginReturnValue.ERROR;
    }

    @Override
    public String cancel() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String finish() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public HashMap<String, StepReturnValue> validate() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Step getStep() {
        // TODO Auto-generated method stub
        return step;
    }

    @Override
    public PluginGuiType getPluginGuiType() {
        // TODO Auto-generated method stub
        return PluginGuiType.NONE;
    }

    @Override
    public String getPagePath() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public PluginType getType() {
        // TODO Auto-generated method stub
        return PluginType.Step;
    }

    @Override
    public String getTitle() {
        // TODO Auto-generated method stub
        return TITLE;
    }

    @Override
    public PluginReturnValue run() {

        return PluginReturnValue.FINISH;
    }

    @Override
    public int getInterfaceVersion() {
        // TODO Auto-generated method stub
        return 0;
    }

}
