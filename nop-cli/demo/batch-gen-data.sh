java -Dnop.config.location=application.yaml -jar ../target/nop-cli-2.0.0-BETA.1.jar run-task v:/batch/batch-gen-demo.task.xml -i="{totalCount:100000,taskKey:'demo'}"