-- Canonical upload status values in Portuguese (UploadStatus JSON / JPA converter).
-- V7 normalized the column to English; this migration switches storage back to PT.

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'uploads'
    ) THEN
        RETURN;
    END IF;

    ALTER TABLE uploads DROP CONSTRAINT IF EXISTS uploads_status_check;

    UPDATE uploads SET status = 'RECEBIDO' WHERE status IN ('RECEIVED', 'RECEBIDO', 'PENDING', 'pending');
    UPDATE uploads SET status = 'EM_PROCESSAMENTO' WHERE status IN ('PROCESSING', 'EM_PROCESSAMENTO', 'IN_PROGRESS', 'in_progress');
    UPDATE uploads SET status = 'ANALISADO' WHERE status IN ('ANALYZED', 'ANALISADO', 'COMPLETED', 'completed', 'SCANNED_OK', 'scanned_ok', 'QUARANTINED', 'quarantined');
    UPDATE uploads SET status = 'ERRO' WHERE status IN ('ERROR', 'ERRO', 'FAILED', 'failed', 'INCONCLUSIVE', 'inconclusive');

    UPDATE uploads SET status = 'RECEBIDO'
    WHERE status IS NOT NULL AND status NOT IN ('RECEBIDO', 'EM_PROCESSAMENTO', 'ANALISADO', 'ERRO');

    ALTER TABLE uploads
        ADD CONSTRAINT uploads_status_check CHECK (status IN ('RECEBIDO', 'EM_PROCESSAMENTO', 'ANALISADO', 'ERRO'));
END $$;
