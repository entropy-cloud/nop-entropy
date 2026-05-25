#!/bin/bash
# Check import ordering in Java source files.
# Expected order: java.* → jakarta.* → third-party → io.nop.*
# Groups are separated by blank lines.

ERRORS=0
FILES_CHECKED=0

check_file() {
    local file="$1"
    FILES_CHECKED=$((FILES_CHECKED + 1))

    # Extract import lines (continuous blocks)
    local in_imports=false
    local prev_group=""
    local line_num=0
    local imports=""
    local prev_import=""
    local in_block=false

    while IFS= read -r line; do
        line_num=$((line_num + 1))
        if [[ "$line" =~ ^import[[:space:]]+(static[[:space:]]+)?([a-zA-Z0-9_.]+) ]]; then
            local full_import="${BASH_REMATCH[2]}"
            imports="$imports"$'\n'"$line_num:$full_import"
        fi
    done < "$file"

    # Check grouping
    local prev_category=""
    local saw_java=false
    local saw_jakarta=false
    local saw_nop=false
    local saw_third=false
    local errors_in_file=false

    while IFS= read -r line; do
        [[ -z "$line" ]] && continue
        local line_no="${line%%:*}"
        local import_text="${line#*:}"

        local category=""
        if [[ "$import_text" == java.* ]]; then
            category="java"
            saw_java=true
        elif [[ "$import_text" == jakarta.* ]]; then
            category="jakarta"
            saw_jakarta=true
            if ! $saw_java && $saw_third; then
                echo "ERROR: $file:$line_no - jakarta import after third-party"
                ERRORS=$((ERRORS + 1))
                errors_in_file=true
            fi
        elif [[ "$import_text" == io.nop.* ]]; then
            category="nop"
            saw_nop=true
        else
            category="third"
            saw_third=true
            if $saw_nop; then
                echo "ERROR: $file:$line_no - third-party import after io.nop.*"
                ERRORS=$((ERRORS + 1))
                errors_in_file=true
            fi
        fi

        if [[ "$prev_category" == "third" && "$category" == "java" ]]; then
            echo "ERROR: $file:$line_no - java.* import after third-party"
            ERRORS=$((ERRORS + 1))
            errors_in_file=true
        fi
        if [[ "$prev_category" == "nop" && "$category" != "nop" ]]; then
            echo "ERROR: $file:$line_no - non-nop import after io.nop.*"
            ERRORS=$((ERRORS + 1))
            errors_in_file=true
        fi
        prev_category="$category"
    done <<< "$imports"

    if $errors_in_file; then
        echo "  -> Import ordering issues in $file"
    fi
}

# Find all Java source files (excluding _gen/ and test directories)
while IFS= read -r file; do
    check_file "$file"
done < <(find /Users/abc/app/nop-entropy-wt/nop-entropy-master/nop-stream \
    -name "*.java" \
    -not -path "*/_gen/*" \
    -not -path "*/test/*" \
    -not -path "*/target/*" \
    -type f)

echo ""
echo "Checked $FILES_CHECKED files."
echo "Errors: $ERRORS"
exit $ERRORS
