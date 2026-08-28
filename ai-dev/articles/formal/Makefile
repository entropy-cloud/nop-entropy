ROOT := .

VOFILES := PatchHunk.vo LensCombinators.vo

.PHONY: all clean

all: $(VOFILES)

%.vo: %.v
	coqc -q $<

clean:
	rm -f *.vo *.glob .*.aux .lia.cache
