package kaist.iclab.mobiletracker.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * Test class for testing database migrations.
 *
 * NOTE: This class currently only verifies the creation of version 1.
 * When updating to version 2:
 * 1. Increment version in TrackerRoomDB.
 * 2. Create an implementation of Migration(1, 2).
 * 3. Add the migration to the database builder in DatabaseModule.
 * 4. Add a test case here to verify the migration.
 *
 * Example:
 * @Test
 * fun migrate1To2() {
 *     var db = helper.createDatabase(TEST_DB, 1).apply {
 *         // Insert some data using SQL queries
 *         execSQL("INSERT INTO ...")
 *         close()
 *     }
 *
 *     // Run migration and validate
 *     db = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)
 *
 *     // Verify data integrity
 * }
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val TEST_DB = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        TrackerRoomDB::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    @Throws(IOException::class)
    fun createDatabase() {
        // Create version 1 database
        helper.createDatabase(TEST_DB, 1).apply {
            close()
        }
    }
}
