#!/bin/bash

################################################################################
# PostgreSQL Database Restore Script
# 
# This script restores a ProfitSaathi database backup with:
# - Interactive backup file selection
# - Safety checks before restore
# - Progress monitoring
# - Error handling and logging
################################################################################

set -euo pipefail

# ============================================================================
# Configuration
# ============================================================================

# Load environment variables
if [ -f "$(dirname "$0")/../.env" ]; then
    export $(grep -v '^#' "$(dirname "$0")/../.env" | xargs)
fi

# Database configuration
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-profitsaathi}"
DB_USER="${DB_USER:-postgres}"
DB_PASSWORD="${DB_PASSWORD:-}"

# Backup configuration
BACKUP_DIR="${BACKUP_DIR:-$(dirname "$0")/../backups}"
LOG_FILE="${BACKUP_DIR}/restore.log"

# ============================================================================
# Functions
# ============================================================================

log() {
    echo "[$(date +'%Y-%m-%d %H:%M:%S')] $*" | tee -a "$LOG_FILE"
}

error() {
    echo "[$(date +'%Y-%m-%d %H:%M:%S')] ERROR: $*" | tee -a "$LOG_FILE" >&2
}

# List available backups
list_backups() {
    log "Available backups:"
    echo ""
    
    local backups=($(find "$BACKUP_DIR" -name "profitsaathi_backup_*.sql.gz" -type f | sort -r))
    
    if [ ${#backups[@]} -eq 0 ]; then
        error "No backups found in $BACKUP_DIR"
        exit 1
    fi
    
    local i=1
    for backup in "${backups[@]}"; do
        local size=$(du -h "$backup" | cut -f1)
        local date=$(stat -f "%Sm" -t "%Y-%m-%d %H:%M:%S" "$backup" 2>/dev/null || stat -c "%y" "$backup" | cut -d'.' -f1)
        echo "  [$i] $(basename "$backup") - $size - $date"
        ((i++))
    done
    
    echo ""
}

# Select backup file
select_backup() {
    local backups=($(find "$BACKUP_DIR" -name "profitsaathi_backup_*.sql.gz" -type f | sort -r))
    
    if [ $# -eq 1 ]; then
        # Backup file provided as argument
        BACKUP_FILE="$1"
        if [ ! -f "$BACKUP_FILE" ]; then
            error "Backup file not found: $BACKUP_FILE"
            exit 1
        fi
    else
        # Interactive selection
        list_backups
        
        read -p "Select backup number to restore (or 'q' to quit): " selection
        
        if [ "$selection" = "q" ]; then
            log "Restore cancelled by user"
            exit 0
        fi
        
        if ! [[ "$selection" =~ ^[0-9]+$ ]] || [ "$selection" -lt 1 ] || [ "$selection" -gt ${#backups[@]} ]; then
            error "Invalid selection"
            exit 1
        fi
        
        BACKUP_FILE="${backups[$((selection-1))]}"
    fi
    
    log "Selected backup: $(basename "$BACKUP_FILE")"
}

# Confirm restore operation
confirm_restore() {
    echo ""
    echo "=========================================="
    echo "WARNING: This will REPLACE the current database!"
    echo "=========================================="
    echo "Database: $DB_NAME"
    echo "Host: $DB_HOST:$DB_PORT"
    echo "Backup: $(basename "$BACKUP_FILE")"
    echo ""
    
    read -p "Are you sure you want to continue? (type 'yes' to confirm): " confirmation
    
    if [ "$confirmation" != "yes" ]; then
        log "Restore cancelled by user"
        exit 0
    fi
}

# Test database connection
test_connection() {
    log "Testing database connection..."
    
    export PGPASSWORD="$DB_PASSWORD"
    
    if ! psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d postgres -c "SELECT 1;" &> /dev/null; then
        error "Failed to connect to database server"
        exit 1
    fi
    
    log "Database connection successful"
}

# Create backup of current database before restore
create_pre_restore_backup() {
    log "Creating pre-restore backup of current database..."
    
    local pre_restore_backup="profitsaathi_pre_restore_$(date +"%Y%m%d_%H%M%S").sql.gz"
    
    export PGPASSWORD="$DB_PASSWORD"
    
    if pg_dump -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" \
        --format=plain --no-owner --no-acl 2>> "$LOG_FILE" | \
        gzip > "${BACKUP_DIR}/${pre_restore_backup}"; then
        
        log "Pre-restore backup created: $pre_restore_backup"
        return 0
    else
        error "Failed to create pre-restore backup"
        return 1
    fi
}

# Drop and recreate database
recreate_database() {
    log "Dropping and recreating database..."
    
    export PGPASSWORD="$DB_PASSWORD"
    
    # Terminate existing connections
    psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d postgres -c \
        "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '$DB_NAME' AND pid <> pg_backend_pid();" \
        2>> "$LOG_FILE" || true
    
    # Drop database
    if ! psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d postgres -c "DROP DATABASE IF EXISTS $DB_NAME;" 2>> "$LOG_FILE"; then
        error "Failed to drop database"
        return 1
    fi
    
    # Create database
    if ! psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d postgres -c "CREATE DATABASE $DB_NAME;" 2>> "$LOG_FILE"; then
        error "Failed to create database"
        return 1
    fi
    
    log "Database recreated successfully"
    return 0
}

# Restore database from backup
restore_database() {
    log "Restoring database from backup..."
    log "This may take several minutes..."
    
    export PGPASSWORD="$DB_PASSWORD"
    
    if gunzip -c "$BACKUP_FILE" | \
        psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" \
        2>> "$LOG_FILE" 1>> "$LOG_FILE"; then
        
        log "Database restored successfully"
        return 0
    else
        error "Database restore failed"
        error "Check log file for details: $LOG_FILE"
        return 1
    fi
}

# Verify restore
verify_restore() {
    log "Verifying restore..."
    
    export PGPASSWORD="$DB_PASSWORD"
    
    # Check if database exists and has tables
    local table_count=$(psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -t -c \
        "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public';" 2>> "$LOG_FILE" | xargs)
    
    if [ -z "$table_count" ] || [ "$table_count" -eq 0 ]; then
        error "Verification failed: No tables found in database"
        return 1
    fi
    
    log "Verification successful: Found $table_count tables"
    return 0
}

# ============================================================================
# Main execution
# ============================================================================

main() {
    log "=========================================="
    log "Starting database restore process"
    log "=========================================="
    
    # Check if backup directory exists
    if [ ! -d "$BACKUP_DIR" ]; then
        error "Backup directory not found: $BACKUP_DIR"
        exit 1
    fi
    
    # Select backup file
    select_backup "$@"
    
    # Confirm restore
    confirm_restore
    
    # Test connection
    test_connection
    
    # Create pre-restore backup
    if ! create_pre_restore_backup; then
        error "Failed to create pre-restore backup. Aborting."
        exit 1
    fi
    
    # Recreate database
    if ! recreate_database; then
        error "Failed to recreate database. Aborting."
        exit 1
    fi
    
    # Restore database
    if ! restore_database; then
        error "Database restore failed"
        exit 1
    fi
    
    # Verify restore
    if ! verify_restore; then
        error "Restore verification failed"
        exit 1
    fi
    
    log "=========================================="
    log "Restore process completed successfully"
    log "=========================================="
    
    echo ""
    echo "✅ Database restored successfully!"
    echo "Database: $DB_NAME"
    echo "Backup: $(basename "$BACKUP_FILE")"
    echo ""
}

# Run main function
main "$@"
