./cli.sh run-task v:/nop/ai/tasks/ai-translate-dir.task.xml \
    -PaiProvider=azure \
    -PaiModel=gpt-5 \
    -PoutputDir=c:/tmp/nop-tmp/output-translate1 \
    -PinputDir=c:/can/nop/nop-entroy/docs \
    -PfromLang=chinese \
    -PtoLang=english