# Agent-style composite fixture for the shell analysis semantic layer.
#
# One script that exercises every capability at once: a heredoc, a pipeline, a
# command substitution, a literal-glued-to-an-expansion concatenation, a for
# loop, a variable-prefixed assignment, a redirection, a background operator and
# a subshell. Phase 4/5/6 end-to-end acceptance all use this file so the
# acceptance is reproducible.
cat <<PATCH | tee /tmp/agent-output.txt
apply_patch <<'INNER
*** Begin Patch
-remove line
+add line
*** End Patch
INNER
PATCH
for target in /tmp/a /tmp/b; do
  echo "processing ${target}/data" > "$target/report.txt" &
done
rm -rf "$WORKDIR"/cache
result=$(ls -la /var/log | grep -v total)
FOO=bar cp /etc/config /tmp/config.bak
bash -c 'rm -rf /tmp/example'
