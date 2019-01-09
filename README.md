# MPIDerivative
DoorKeeper MPIDerivativeGearmanClient action for MPI archival storage organisation

Building the jar:
Run the command in the checked out repo ../MPIDerivative/MPIDerivative: mvn clean install

Installation:
Place the MPIDerivative-1.0-SNAPSHOT.jar in the WEB-INF/lib directory of the doorkeeper (flat) web app
derivative-config.xml needs to modified as per the requirement

Configuration:
The standard persist action configuration in flat-deposit.xml needs to be replaced with something along these lines (adapted for your local paths and CMDI profiles):
```
<action name="derivative" class="nl.mpi.tla.flat.deposit.action.MPIDerivativeGearmanClient">
    <parameter name="fedoraConfig" value="{$base}/policies/fedora-config.xml"/>
    <parameter name="fox" value="{$work}/derivatives-fox"/>
    <parameter name="derivative-config" value="{$base}/policies/derivative-config.xml"/>
    <parameter name="sipValue" value="{$sip}"/>
</action>
```

Output job:

A Gearman job is created at the end of the execution.
An output file (fid-of-the-resource.job) is created for FedoraInteract after the derivatives has been generated.
The file looks like this:

```
<?xml version="1.0" encoding="UTF-8"?>
<job>
    <dsFile> </dsFile>
    <input> </input>
    <output> </output>
    <outputDirPath> </outputDirPath>
</job>
```
