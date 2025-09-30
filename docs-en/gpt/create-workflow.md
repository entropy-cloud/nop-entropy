You are a computer expert, proficient in concepts such as meta-models and metadata. You need to analyze the following requirement description and derive the workflow model definition related to the process. The returned result must comply with the following meta-model constraints

* Use English for name and stepName; use Chinese for displayName

```xml

<workflow displayName="chinese">

    <description/>

    <start startStepName="english" >

    </start>

    <end>
    </end>


    <actions>
        <!--
          @local Whether it is a local action; does not cause the current step to end
          @common Whether it is a common action that every step has
          @forActivated Whether it can be invoked when the step is in the active state
          @forHistory Whether it can be invoked when the step is in the historical state
          @forWaiting Whether it can be invoked when the workflow step is in the waiting state
          @forFlowEnded Whether it can be invoked after the workflow has ended
          @forReject Whether it is a reject action; a reject action may have no step transition configured
          @forWithdraw Whether it is a withdraw action; a withdraw action may have no step transition configured
        -->
        <action name="!english" displayName="chinese" local="!boolean=false" common="!boolean=false"
                internal="!boolean=false" group="string" sortOrder="!int=0"
                forActivated="!boolean=true" forHistory="!boolean=false" saveActionRecord="!boolean=true"
                forWaiting="!boolean=false" forReject="!boolean=false" forWithdraw="!boolean=false"
                forFlowEnded="!boolean=false">

            <description/>

            <when>condition-expr</when>

            <source>javascript-code</source>

            <!--
              @splitType Branch type: and means every branch is executed; or means evaluate from top to bottom and execute only the first transition target whose condition is met. Default is and
            -->
            <transition splitType="and|or" onAppStates="csv-set">

                <to-step stepName="!string">
                      <when>condition-expr</when>
                </to-step>

            </transition>

        </action>
    </actions>

    <steps>
        <!--
          @internal Steps marked as internal will not be displayed in the UI
          @optional Whether this step is an optional step; if not, an exception in this step will propagate to the parent node and may ultimately cause the entire process to terminate

        -->
        <step name="!english" displayName="chinese" waitSignals="csv-set"
              internal="!boolean=false"
              optional="!boolean=false"
              tagSet="csv-set" allowWithdraw="!boolean=false"
              allowReject="!boolean=false" dueAction="string">

            <!-- If there is no automatic transition, an assignment is required -->
            <assignment>
               <actor actorName="string" actorType="role|user|group" actorId="string" />
            </assignment>

            <ref-actions>
                <ref-action name="!english"/>
            </ref-actions>

        </step>

    </steps>

</workflow>
```

The requirement description is as follows:

```
Please design an employee conversion to permanent status application process that requires approval from the team leader and the manager. If the employee’s level exceeds 10, approval from the general manager is also required.
```

The process design must meet the above meta-model requirements, and the result must be returned in XML format only.
<!-- SOURCE_MD5:51053be295a4ede0911b4bd97f33957c-->
