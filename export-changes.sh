#!/bin/bash

set -euo pipefail

MAIN_BRANCH="master"
DIFF_FILE="changes.diff"
ZIP_FILE="changes.zip"
TEMP_DIR=".temp_export_$$"

usage() {
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Export changes from current branch to main branch"
    echo ""
    echo "Options:"
    echo "  -f, --format FORMAT  Export format: 'diff' (default) or 'zip'"
    echo "  -b, --branch BRANCH  Main branch name (default: master)"
    echo "  -o, --output FILE   Output file name (default: changes.diff or changes.zip)"
    echo "  -h, --help         Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0                          # Export as diff to changes.diff"
    echo "  $0 -f zip                # Export as zip to changes.zip"
    echo "  $0 -f diff -o my.patch  # Export as diff to my.patch"
    echo "  $0 -b dev -f zip         # Export changes from dev branch"
}

error_exit() {
    echo "Error: $1" >&2
    exit 1
}

parse_args() {
    FORMAT="diff"
    OUTPUT_FILE=""

    while [[ $# -gt 0 ]]; do
        case $1 in
            -f|--format)
                FORMAT="$2"
                shift 2
                ;;
            -b|--branch)
                MAIN_BRANCH="$2"
                shift 2
                ;;
            -o|--output)
                OUTPUT_FILE="$2"
                shift 2
                ;;
            -h|--help)
                usage
                exit 0
                ;;
            *)
                error_exit "Unknown option: $1"
                ;;
        esac
    done

    if [[ -n "$OUTPUT_FILE" ]]; then
        if [[ "$FORMAT" == "diff" ]]; then
            DIFF_FILE="$OUTPUT_FILE"
        else
            ZIP_FILE="$OUTPUT_FILE"
        fi
    fi
}

check_git_repo() {
    if ! git rev-parse --git-dir > /dev/null 2>&1; then
        error_exit "Not in a git repository"
    fi
}

get_current_branch() {
    git rev-parse --abbrev-ref HEAD
}

check_branch() {
    local current=$1
    local main=$2

    if [[ "$current" == "$main" ]]; then
        error_exit "Current branch is already '$main'. No changes to export."
    fi

    if ! git show-ref --verify --quiet "refs/heads/$main" 2>/dev/null; then
        error_exit "Main branch '$main' does not exist locally"
    fi
}

export_as_diff() {
    local current=$1
    local main=$2
    local output=$3

    echo "Generating diff from '$current' to '$main'..."

    local diff_range="${main}...${current}"

    if git diff --exit-code "$diff_range" > /dev/null 2>&1; then
        echo "No changes found between '$current' and '$main'"
        return 0
    fi

    git diff "$diff_range" > "$output"

    local added=$(git diff --diff-filter=A --name-only "$diff_range" | wc -l | tr -d ' ')
    local modified=$(git diff --diff-filter=M --name-only "$diff_range" | wc -l | tr -d ' ')
    local deleted=$(git diff --diff-filter=D --name-only "$diff_range" | wc -l | tr -d ' ')

    echo "Diff exported to: $output"
    echo "Summary:"
    echo "  Added files:    $added"
    echo "  Modified files: $modified"
    echo "  Deleted files:  $deleted"

    return 0
}

export_as_zip() {
    local current=$1
    local main=$2
    local output=$3
    local temp_dir=$4

    echo "Exporting changed files from '$current' to '$main'..."

    local diff_range="${main}...${current}"

    if git diff --exit-code "$diff_range" > /dev/null 2>&1; then
        echo "No changes found between '$current' and '$main'"
        return 0
    fi

    rm -rf "$temp_dir"
    mkdir -p "$temp_dir"

    local added_files=$(git diff --diff-filter=A --name-only "$diff_range")
    local modified_files=$(git diff --diff-filter=M --name-only "$diff_range")

    local total_files=0

    if [[ -n "$added_files" ]]; then
        while IFS= read -r file; do
            if [[ -n "$file" ]]; then
                local dir="$temp_dir/$(dirname "$file")"
                mkdir -p "$dir"
                cp "$file" "$dir/"
                total_files=$((total_files + 1))
            fi
        done <<< "$added_files"
    fi

    if [[ -n "$modified_files" ]]; then
        while IFS= read -r file; do
            if [[ -n "$file" ]]; then
                local dir="$temp_dir/$(dirname "$file")"
                mkdir -p "$dir"
                cp "$file" "$dir/"
                total_files=$((total_files + 1))
            fi
        done <<< "$modified_files"
    fi

    local deleted_count=$(git diff --diff-filter=D --name-only "$diff_range" | wc -l | tr -d ' ')
    local added_count=$(git diff --diff-filter=A --name-only "$diff_range" | wc -l | tr -d ' ')
    local modified_count=$(git diff --diff-filter=M --name-only "$diff_range" | wc -l | tr -d ' ')

    if [[ $total_files -eq 0 ]]; then
        echo "No files to export (only deletions detected)"
        rm -rf "$temp_dir"
        return 0
    fi

    cd "$temp_dir"
    zip -rq "../$(basename "$output")" .
    cd - > /dev/null

    mv "${temp_dir}/../$(basename "$output")" "$output"
    rm -rf "$temp_dir"

    echo "ZIP exported to: $output"
    echo "Summary:"
    echo "  Added files:    $added_count"
    echo "  Modified files: $modified_count"
    echo "  Deleted files:  $deleted_count"
    echo "  Total exported: $total_files"

    return 0
}

main() {
    parse_args "$@"
    check_git_repo

    local current_branch=$(get_current_branch)
    check_branch "$current_branch" "$MAIN_BRANCH"

    echo "Current branch: $current_branch"
    echo "Main branch:    $MAIN_BRANCH"
    echo "Export format:   $FORMAT"
    echo ""

    case "$FORMAT" in
        diff)
            export_as_diff "$current_branch" "$MAIN_BRANCH" "$DIFF_FILE"
            ;;
        zip)
            export_as_zip "$current_branch" "$MAIN_BRANCH" "$ZIP_FILE" "$TEMP_DIR"
            ;;
        *)
            error_exit "Invalid format: $FORMAT. Use 'diff' or 'zip'"
            ;;
    esac

    echo ""
    echo "Next steps:"
    if [[ "$FORMAT" == "diff" ]]; then
        echo "  1. Switch to $MAIN_BRANCH: git checkout $MAIN_BRANCH"
        echo "  2. Apply the diff:   git apply $DIFF_FILE"
    else
        echo "  1. Extract the zip:   unzip $ZIP_FILE"
        echo "  2. Review and merge files manually"
    fi
    echo "  3. Review changes:   git status"
    echo "  4. Commit when ready: git add . && git commit -m 'Merge changes'"
    echo ""
    echo "Note: Do NOT push directly. Review carefully before committing."
}

main "$@"
