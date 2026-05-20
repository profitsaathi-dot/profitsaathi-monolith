#!/bin/bash

################################################################################
# Setup Automated Database Backups with Cron
# 
# This script configures cron to run automated backups
################################################################################

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKUP_SCRIPT="${SCRIPT_DIR}/backup-database.sh"

echo "=========================================="
echo "Setting up automated database backups"
echo "=========================================="
echo ""

# Check if backup script exists
if [ ! -f "$BACKUP_SCRIPT" ]; then
    echo "ERROR: Backup script not found: $BACKUP_SCRIPT"
    exit 1
fi

# Make backup script executable
chmod +x "$BACKUP_SCRIPT"

echo "Backup script: $BACKUP_SCRIPT"
echo ""
echo "Choose backup frequency:"
echo "  1) Daily at 2:00 AM"
echo "  2) Daily at 3:00 AM"
echo "  3) Twice daily (2:00 AM and 2:00 PM)"
echo "  4) Every 6 hours"
echo "  5) Custom schedule"
echo ""

read -p "Select option (1-5): " option

case $option in
    1)
        CRON_SCHEDULE="0 2 * * *"
        DESCRIPTION="Daily at 2:00 AM"
        ;;
    2)
        CRON_SCHEDULE="0 3 * * *"
        DESCRIPTION="Daily at 3:00 AM"
        ;;
    3)
        CRON_SCHEDULE="0 2,14 * * *"
        DESCRIPTION="Twice daily (2:00 AM and 2:00 PM)"
        ;;
    4)
        CRON_SCHEDULE="0 */6 * * *"
        DESCRIPTION="Every 6 hours"
        ;;
    5)
        echo ""
        echo "Enter custom cron schedule (e.g., '0 3 * * *' for daily at 3 AM):"
        read -p "Schedule: " CRON_SCHEDULE
        DESCRIPTION="Custom: $CRON_SCHEDULE"
        ;;
    *)
        echo "Invalid option"
        exit 1
        ;;
esac

echo ""
echo "Selected schedule: $DESCRIPTION"
echo "Cron expression: $CRON_SCHEDULE"
echo ""

# Create cron job entry
CRON_JOB="$CRON_SCHEDULE $BACKUP_SCRIPT >> ${SCRIPT_DIR}/../backups/cron.log 2>&1"

echo "Cron job to be added:"
echo "$CRON_JOB"
echo ""

read -p "Add this cron job? (yes/no): " confirm

if [ "$confirm" != "yes" ]; then
    echo "Setup cancelled"
    exit 0
fi

# Add cron job
(crontab -l 2>/dev/null || true; echo "$CRON_JOB") | crontab -

echo ""
echo "✅ Cron job added successfully!"
echo ""
echo "To view your cron jobs:"
echo "  crontab -l"
echo ""
echo "To remove this cron job:"
echo "  crontab -e"
echo "  (then delete the line with backup-database.sh)"
echo ""
echo "Backup logs will be written to:"
echo "  ${SCRIPT_DIR}/../backups/backup.log"
echo "  ${SCRIPT_DIR}/../backups/cron.log"
echo ""
