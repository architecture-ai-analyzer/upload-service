-- Align uploads.status CHECK with UploadStatus (JPA @Enumerated STRING): RECEIVED, PROCESSING, ANALYZED, ERROR.
-- PostgreSQL: older schemas often allowed only Portuguese (RECEBIDO, …), causing RECEIVED inserts to fail.

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'uploads'
    ) THEN
        RETURN;
    END IF;

    ALTER TABLE uploads DROP CONSTRAINT IF EXISTS uploads_status_check;

    UPDATE uploads SET status = 'RECEIVED' WHERE status IN ('RECEBIDO', 'PENDING', 'pending');
    UPDATE uploads SET status = 'PROCESSING' WHERE status IN ('EM_PROCESSAMENTO', 'IN_PROGRESS', 'in_progress');
    UPDATE uploads SET status = 'ANALYZED' WHERE status IN ('ANALISADO', 'COMPLETED', 'completed', 'SCANNED_OK', 'scanned_ok', 'QUARANTINED', 'quarantined');
    UPDATE uploads SET status = 'ERROR' WHERE status IN ('ERRO', 'FAILED', 'failed', 'INCONCLUSIVE', 'inconclusive');

    UPDATE uploads SET status = 'RECEIVED'
    WHERE status IS NOT NULL AND status NOT IN ('RECEIVED', 'PROCESSING', 'ANALYZED', 'ERROR');

    ALTER TABLE uploads
        ADD CONSTRAINT uploads_status_check CHECK (status IN ('RECEIVED', 'PROCESSING', 'ANALYZED', 'ERROR'));
END $$;
