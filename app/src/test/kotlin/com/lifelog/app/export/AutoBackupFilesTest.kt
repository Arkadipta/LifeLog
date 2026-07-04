package com.lifelog.app.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Locks down the auto-backup file-management contract:
 * [rotateAutoBackups] keeps only the newest [AUTO_BACKUP_MAX_KEEP] backups of
 * any format, while [restorableAutoBackupsIn] offers only `.db` files (the one
 * format the restore pipeline accepts), newest first.
 */
class AutoBackupFilesTest {

    @get:Rule
    val tmp = TemporaryFolder()

    /** Creates a backup-dir file with a deterministic lastModified ordering. */
    private fun File.backupFile(name: String, ageRank: Int): File =
        resolve(name).apply {
            writeText("x")
            // Newest = rank 0. Whole seconds, since some filesystems truncate.
            assertTrue(setLastModified(1_700_000_000_000L - ageRank * 1000L))
        }

    // ── rotateAutoBackups ─────────────────────────────────────────────────────

    @Test
    fun rotate_keepsNewestMaxKeep_deletesOlder() {
        val dir = tmp.newFolder("backups")
        val files = (0 until 9).map { dir.backupFile("lifelog_backup_$it.db", ageRank = it) }

        rotateAutoBackups(dir, maxKeep = AUTO_BACKUP_MAX_KEEP)

        assertEquals(files.take(7).toSet(), dir.listFiles()!!.toSet())
    }

    @Test
    fun rotate_underMaxKeep_deletesNothing() {
        val dir = tmp.newFolder("backups")
        val files = (0 until 3).map { dir.backupFile("lifelog_backup_$it.db", ageRank = it) }

        rotateAutoBackups(dir, maxKeep = AUTO_BACKUP_MAX_KEEP)

        assertEquals(files.toSet(), dir.listFiles()!!.toSet())
    }

    @Test
    fun rotate_agesOutLegacyFormats() {
        val dir = tmp.newFolder("backups")
        // 7 fresh .db backups plus two older legacy-format ones.
        val kept = (0 until 7).map { dir.backupFile("lifelog_backup_$it.db", ageRank = it) }
        dir.backupFile("lifelog_backup_legacy.json", ageRank = 8)
        dir.backupFile("lifelog_backup_legacy.zip", ageRank = 9)

        rotateAutoBackups(dir, maxKeep = AUTO_BACKUP_MAX_KEEP)

        assertEquals(kept.toSet(), dir.listFiles()!!.toSet())
    }

    @Test
    fun rotate_countsLegacyFormatsTowardMaxKeep() {
        val dir = tmp.newFolder("backups")
        // A legacy backup newer than every .db file occupies one of the 7 slots.
        val legacy = dir.backupFile("lifelog_backup_legacy.json", ageRank = 0)
        val dbs = (1..7).map { dir.backupFile("lifelog_backup_$it.db", ageRank = it) }

        rotateAutoBackups(dir, maxKeep = AUTO_BACKUP_MAX_KEEP)

        assertEquals((dbs.take(6) + legacy).toSet(), dir.listFiles()!!.toSet())
    }

    @Test
    fun rotate_ignoresUnrelatedFiles() {
        val dir = tmp.newFolder("backups")
        val unrelated = dir.backupFile("not_a_backup.db", ageRank = 99)
        val backups = (0 until 8).map { dir.backupFile("lifelog_backup_$it.db", ageRank = it) }

        rotateAutoBackups(dir, maxKeep = AUTO_BACKUP_MAX_KEEP)

        // The oldest true backup is gone; the unrelated file is neither counted nor deleted.
        assertEquals((backups.take(7) + unrelated).toSet(), dir.listFiles()!!.toSet())
    }

    // ── restorableAutoBackupsIn ───────────────────────────────────────────────

    @Test
    fun list_offersOnlyDbFiles_newestFirst() {
        val dir = tmp.newFolder("backups")
        val old = dir.backupFile("lifelog_backup_old.db", ageRank = 5)
        val new = dir.backupFile("lifelog_backup_new.db", ageRank = 1)
        dir.backupFile("lifelog_backup_legacy.json", ageRank = 0)
        dir.backupFile("lifelog_backup_legacy.zip", ageRank = 2)
        dir.backupFile("unrelated.db", ageRank = 3)

        assertEquals(listOf(new, old), restorableAutoBackupsIn(dir))
    }

    @Test
    fun list_missingDir_isEmpty() {
        assertEquals(emptyList<File>(), restorableAutoBackupsIn(File(tmp.root, "nope")))
    }

    @Test
    fun list_ignoresSubdirectories() {
        val dir = tmp.newFolder("backups")
        tmp.newFolder("backups", "lifelog_backup_dir.db")

        assertEquals(emptyList<File>(), restorableAutoBackupsIn(dir))
    }

    // ── Generalized core (shared by the app-storage and SAF-folder backends) ──

    private fun candidate(name: String, ageRank: Int) =
        BackupCandidate(handle = name, name = name, modifiedAt = 1_000_000L - ageRank)

    /**
     * Some SAF providers may append a MIME-derived extension to a created
     * document ("lifelog_backup_x.db.bin"). Such a file must never be offered
     * for restore, but rotation still counts it and ages it out.
     */
    @Test
    fun mangledSafName_isNotRestorable_butIsRotated() {
        val ok = candidate("lifelog_backup_1.db", ageRank = 1)
        val mangled = candidate("lifelog_backup_2.db.bin", ageRank = 0)

        assertEquals(listOf(ok), restorableBackups(listOf(mangled, ok)))
        assertEquals(
            listOf(ok.handle),
            rotationVictims(listOf(mangled, ok), maxKeep = 1)
        )
    }

    @Test
    fun restorable_sortsNewestFirst_regardlessOfInputOrder() {
        val newest = candidate("lifelog_backup_a.db", ageRank = 0)
        val middle = candidate("lifelog_backup_b.db", ageRank = 1)
        val oldest = candidate("lifelog_backup_c.db", ageRank = 2)

        assertEquals(
            listOf(newest, middle, oldest),
            restorableBackups(listOf(middle, oldest, newest))
        )
    }

    @Test
    fun rotationVictims_returnsOldestBeyondMaxKeep_neverUnrelatedNames() {
        val backups = (0 until 9).map { candidate("lifelog_backup_$it.db", ageRank = it) }
        val unrelated = candidate("keep_me.db", ageRank = 99)

        assertEquals(
            listOf(backups[7].handle, backups[8].handle),
            rotationVictims(backups.shuffled() + unrelated, maxKeep = 7)
        )
    }
}
