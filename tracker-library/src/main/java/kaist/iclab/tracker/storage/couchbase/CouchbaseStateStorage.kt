package kaist.iclab.tracker.storage.couchbase

import android.util.Log
import com.couchbase.lite.MutableDocument
import com.google.gson.Gson
import kaist.iclab.tracker.storage.core.StateStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

open class CouchbaseStateStorage<T>(
    couchbase: CouchbaseDB,
    private val defaultVal: T,
    private val clazz: Class<T>,
    private val collectionName: String
) : StateStorage<T> {
    private val _stateFlow = MutableStateFlow(defaultVal)
    override val stateFlow: StateFlow<T>
        get()
        = _stateFlow

    private val collection = couchbase.getCollection(collectionName)
    private val gson = Gson()

    init {
        _stateFlow.value = get()
        Log.d("CouchbaseStateStorage", "initialize")
    }

    override fun set(value: T) {
        Log.d("CouchbaseStateStorage", "set: $value")
        val json = gson.toJson(value)

        val existingDoc = collection.getDocument(collectionName)
        val mutableDoc = if (existingDoc != null) {
            Log.d(this::class.simpleName, "Mutating the existing doc...")
            existingDoc.toMutable().setJSON(json)
        } else {
            Log.d(this::class.simpleName, "Doc doesn't exist!")
            MutableDocument(collectionName, json)
        }

        collection.save(mutableDoc)
        _stateFlow.value = value
    }

    override fun get(): T {
        val document = collection.getDocument(collectionName)
        if (document == null) {
            set(defaultVal)
            return defaultVal
        }

        return try {
            val json = document.toJSON()
            gson.fromJson(json, clazz) ?: defaultVal
        } catch (e: Exception) {
            // Handle data migration or corrupted data gracefully
            Log.w(
                TAG,
                "Error deserializing '$collectionName': ${e.message}. Clearing corrupted data."
            )
            try {
                collection.delete(document)
            } catch (deleteError: Exception) {
                Log.e(TAG, "Error deleting corrupted document: ${deleteError.message}")
            }
            defaultVal
        }
    }

    companion object {
        private const val TAG = "CouchbaseStateStorage"
    }
}