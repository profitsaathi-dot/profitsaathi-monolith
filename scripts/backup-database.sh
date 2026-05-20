#!/bin/bash

################################################################################
# PostgreSQL Database Backup Script
# 
# This script creates automated backups of the ProfitSaathi database with:
# - Timestamped backup files
# - Compression to save space
# - Retention policy (keeps last 30 days)
# - Error handling and logging
# - Optional upload to cloud storage (S3/GCS)
################################################################################

set -euo pipefail  # Exit on error, undefined variables, and pipe failures

# ============================================================================
# Configuration
# ============================================================================

# Load environment variables from .env file if it exists
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
RETENTION_DAYS="${RETENTION_DAYS:-30}"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
BACKUP_FILE="profitsaathi_backup_${TIMESTAMP}.sql.gz"
LOG_FILE="${BACKUP_DIR}/backup.log"

# Cloud storage configuration (optional)
ENABLE_S3_UPLOAD="${ENABLE_S3_UPLOAD:-false}"
S3_BUCKET="${S3_BUCKET:-}"
S3_PREFIX="${S3_PREFIX:-backups/postgresql}"

ENABLE_GCS_UPLOAD="${ENABLE_GCS_UPLOAD:-false}"
GCS_BUCKET="${GCS_BUCKET:-}"
GCS_PREFIX="${GCS_PREFIX:-backups/postgresql}"

# ============================================================================
# Functions
# ============================================================================

log() {
    echo "[$(date +'%Y-%m-%d %H:%M:%S')] $*" | tee -a "$LOG_FILE"
}

error() {
    echo "[$(date +'%Y-%m-%d %H:%M:%S')] ERROR: $*" | tee -a "$LOG_FILE" >&2
}

# Create backup directory if it doesn't exist
create_backup_dir() {
    if [ ! -d "$BACKUP_DIR" ]; then
        log "Creating backup directory: $BACKUP_DIR"
        mkdir -p "$BACKUP_DIR"
    fi
}

# Check if required tools are installed
check_dependencies() {
    local missing_deps=()
    
    if ! command -v pg_dump &> /dev/null; then
        missing_deps+=("pg_dump (postgresql-client)")
    fi
    
    if ! command -v gzip &> /dev/null; then
        missing_deps+=("gzip")
    fi
    
    if [ "$ENABLE_S3_UPLOAD" = "true" ] && ! command -v aws &> /dev/null; then
        missing_deps+=("aws-cli")
    fi
    
    if [ "$ENABLE_GCS_UPLOAD" = "true" ] && ! command -v gsutil &> /dev/null; then
        missing_deps+=("gsutil (google-cloud-sdk)")
    fi
    
    if [ ${#missing_deps[@]} -gt 0 ]; then
        error "Missing required dependencies: ${missing_deps[*]}"
        error "Please install them before running this script"
        exit 1
    fi
}

# Test database connection
test_connection() {
    log "Testing database connection..."
    
    export PGPASSWORD="$DB_PASSWORD"
    
    if ! psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -c "SELECT 1;" &> /dev/null; then
        error "Failed to connect to database"
        error "Host: $DB_HOST:$DB_PORT, Database: $DB_NAME, User: $DB_USER"
        exit 1
    fi
    
    log "Database connection successful"
}

# Create database backup
create_backup() {
    log "Starting database backup..."
    log "Database: $DB_NAME"
    log "Backup file: $BACKUP_FILE"
    
    export PGPASSWORD="$DB_PASSWORD"
    
    # Create backup with pg_dump and compress with gzip
    if pg_dump -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" \
        --format=plain \
        --no-owner \
        --no-acl \
        --verbose \
        2>> "$LOG_FILE" | gzip > "${BACKUP_DIR}/${BACKUP_FILE}"; then
        
        local backup_size=$(du -h "${BACKUP_DIR}/${BACKUP_FILE}" | cut -f1)
        log "Backup created successfully: ${BACKUP_FILE} (${backup_size})"
        return 0
    else
        error "Backup failed"
        return 1
    fi
}

# Upload backup to AWS S3
upload_to_s3() {
    if [ "$ENABLE_S3_UPLOAD" != "true" ]; then
        return 0
    fi
    
    if [ -z "$S3_BUCKET" ]; then
        error "S3_BUCKET not configured"
        return 1
    fi
    
    log "Uploading backup to S3..."
    log "Bucket: s3://${S3_BUCKET}/${S3_PREFIX}/"
    
    if aws s3 cp "${BACKUP_DIR}/${BACKUP_FILE}" \
        "s3://${S3_BUCKET}/${S3_PREFIX}/${BACKUP_FILE}" \
        --storage-class STANDARD_IA \
        2>> "$LOG_FILE"; then
        
        log "Backup uploaded to S3 successfully"
        return 0
    else
        error "Failed to upload backup to S3"
        return 1
    fi
}

# Upload backup to Google Cloud Storage
upload_to_gcs() {
    if [ "$ENABLE_GCS_UPLOAD" != "true" ]; then
        return 0
    fi
    
    if [ -z "$GCS_BUCKET" ]; then
        error "GCS_BUCKET not configured"
        return 1
    fi
    
    log "Uploading backup to GCS..."
    log "Bucket: gs://${GCS_BUCKET}/${GCS_PREFIX}/"
    
    if gsutil cp "${BACKUP_DIR}/${BACKUP_FILE}" \
        "gs://${GCS_BUCKET}/${GCS_PREFIX}/${BACKUP_FILE}" \
        2>> "$LOG_FILE"; then
        
        log "Backup uploaded to GCS successfully"
        return 0
    else
        error "Failed to upload backup to GCS"
        return 1
    fi
}

# Clean up old backups based on retention policy
cleanup_old_backups() {
    log "Cleaning up backups older than ${RETENTION_DAYS} days..."
    
    local deleted_count=0
    
    # Find and delete old local backups
    while IFS= read -r -d '' file; do
        log "Deleting old backup: $(basename "$file")"
        rm -f "$file"
        ((deleted_count++))
    done < <(find "$BACKUP_DIR" -name "profitsaathi_backup_*.sql.gz" -type f -mtime +${RETENTION_DAYS} -print0)
    
    if [ $deleted_count -gt 0 ]; then
        log "Deleted $deleted_count old backup(s)"
    else
        log "No old backups to delete"
    fi
    
    # Clean up old S3 backups if enabled
    if [ "$ENABLE_S3_UPLOAD" = "true" ] && [ -n "$S3_BUCKET" ]; then
        log "Cleaning up old S3 backups..."
        local cutoff_date=$(date -d "${RETENTION_DAYS} days ago" +%Y%m%d 2>/dev/null || date -v-${RETENTION_DAYS}d +%Y%m%d)
        
        aws s3 ls "s3://${S3_BUCKET}/${S3_PREFIX}/" 2>/dev/null | \
        awk '{print $4}' | \
        grep "profitsaathi_backup_" | \
        while read -r s3_file; do
            local file_date=$(echo "$s3_file" | grep -oP '\d{8}' | head -1)
            if [ -n "$file_date" ] && [ "$file_date" -lt "$cutoff_date" ]; then
                log "Deleting old S3 backup: $s3_file"
                aws s3 rm "s3://${S3_BUCKET}/${S3_PREFIX}/${s3_file}" 2>> "$LOG_FILE"
            fi
        done
    fi
}

# Send notification (optional - implement based on your needs)
send_notification() {
    local status=$1
    local message=$2
    
    # TODO: Implement notification (email, Slack, etc.)
    # Example: curl -X POST https://hooks.slack.com/... -d "{\"text\":\"$message\"}"
    
    log "Notification: $message"
}

# ============================================================================
# Main execution
# ============================================================================

main() {
    log "=========================================="
    log "Starting database backup process"
    log "=========================================="
    
    # Create backup directory
    create_backup_dir
    
    # Check dependencies
    check_dependencies
    
    # Test database connection
    test_connection
    
    # Create backup
    if ! create_backup; then
        send_notification "FAILED" "Database backup failed for $DB_NAME"
        exit 1
    fi
    
    # Upload to cloud storage
    upload_to_s3
    upload_to_gcs
    
    # Clean up old backups
    cleanup_old_backups
    
    log "=========================================="
    log "Backup process completed successfully"
    log "=========================================="
    
    send_notification "SUCCESS" "Database backup completed successfully for $DB_NAME"
}

# Run main function
main "$@"
