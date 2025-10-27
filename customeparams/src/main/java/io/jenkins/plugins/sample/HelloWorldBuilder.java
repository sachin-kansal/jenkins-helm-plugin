package io.jenkins.plugins.sample;

import hudson.Launcher;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.Builder;
import hudson.tasks.BuildStepDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import hudson.util.ListBoxModel;

import javax.servlet.ServletException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import jenkins.tasks.SimpleBuildStep;
import net.sf.json.JSONArray;

import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundSetter;

public class HelloWorldBuilder extends Builder implements SimpleBuildStep {

    private final String releaseName;   
    private String revision;
    private String kubeconfigPath;

    @DataBoundConstructor
    public HelloWorldBuilder(String releaseName) {
        this.releaseName = releaseName;
    }

    public String getReleaseName() {
        return releaseName;
    }
    public String getKubeconfigPath() {
        return kubeconfigPath;
    }

    public String getRevision() {
        return revision;
    }

    @DataBoundSetter
    public void setRevision(String revision) {
        this.revision = revision;
    }
    @DataBoundSetter
    public void setKubeconfigPath(String kubeconfigPath) {
        this.kubeconfigPath = kubeconfigPath;
    }

    @Override
    public void perform(Run<?, ?> run, FilePath workspace, EnvVars env, Launcher launcher,
                        TaskListener listener) throws InterruptedException, IOException {

        listener.getLogger().println("Fetching Helm history for release: " + releaseName);

        List<String> revisions = getHelmRevisions(releaseName, kubeconfigPath);

        listener.getLogger().println("Available revisions: " + revisions);

        if (revision != null && !revision.isEmpty()) {
            listener.getLogger().println("Selected revision: " + revision);
            listener.getLogger().println("Rollback logic will be added here ✅");
        }
    }

    private List<String> getHelmRevisions(String release, String kubeconfigPath)
        throws IOException, InterruptedException {

    List<String> revisions = new ArrayList<>();

    ProcessBuilder pb = new ProcessBuilder(
            "helm", "history", release, "-o", "json",
            "--kubeconfig", kubeconfigPath
    );

    Process process = pb.start();

    BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()));
    StringBuilder output = new StringBuilder();
    String line;
    while ((line = br.readLine()) != null) {
        output.append(line);
    }

    if (process.waitFor() == 0 && !output.toString().isEmpty()) {
        JSONArray arr = JSONArray.fromObject(output.toString());
        for (int i = 0; i < arr.size(); i++) {
            revisions.add(arr.getJSONObject(i).getString("revision"));
        }
    }

    return revisions;
    }




    @Symbol("helmHistory")
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        public FormValidation doCheckReleaseName(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.isEmpty())
                return FormValidation.error("Release Name Required");
            return FormValidation.ok();
        }

        public String getFunctionName() {return "helmHistory";}

    public ListBoxModel doFillRevisionItems(@QueryParameter String releaseName,
                                        @QueryParameter String kubeconfigPath) {
    ListBoxModel items = new ListBoxModel();

    if (releaseName == null || releaseName.isEmpty()) {
        return items;}
    try {
        List<String> revisions = new HelloWorldBuilder(releaseName).getHelmRevisions(releaseName, kubeconfigPath);
        for (String r : revisions) {
            items.add(r);
        }
    } catch (Exception ignored) {}
    return items;
    }



        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "helm HIstory lookup";
        }

    }

}
