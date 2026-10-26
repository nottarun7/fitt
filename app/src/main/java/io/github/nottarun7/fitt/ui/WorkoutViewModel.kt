package io.github.nottarun7.fitt.ui

import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nottarun7.fitt.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class WorkoutOverview(
    val routines: List<RoutineWithExercises> = emptyList(),
    val active: SessionWithExercises? = null,
    val history: List<HistorySummary> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

data class EditorState(val key: String, val draft: RoutineDraft, val dirty: Boolean = false)

class WorkoutViewModel(private val repository: WorkoutRepository, private val savedState: SavedStateHandle) : ViewModel() {
    val overview = combine(repository.routines, repository.active, repository.history) { routines, active, history ->
        WorkoutOverview(routines, active, history, loading = false)
    }.catch { emit(WorkoutOverview(loading = false, error = "Could not open your workouts. Close and reopen Fitt to retry.")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutOverview())

    private val _editor = MutableStateFlow(savedState.get<Bundle>("editor")?.toEditor())
    val editor = _editor.asStateFlow()
    private val _pendingWrites = MutableStateFlow(0)
    val saving = _pendingWrites.map { it > 0 }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    private val _pendingActions = MutableStateFlow(0)
    val busy = _pendingActions.map { it > 0 }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    private val notices = Channel<String>(Channel.BUFFERED)
    val messages = notices.receiveAsFlow()
    private data class Command(val action: Boolean, val block: suspend () -> Unit)
    private val commands = Channel<Command>(Channel.UNLIMITED)

    init {
        viewModelScope.launch {
            for (command in commands) {
                try {
                    command.block()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (invalid: IllegalArgumentException) {
                    notices.send(invalid.message ?: "Check your entries and try again.")
                } catch (_: Exception) {
                    notices.send("Could not save this change. Try again.")
                } finally {
                    _pendingWrites.value--
                    if (command.action) _pendingActions.value--
                }
            }
        }
    }

    private fun enqueue(action: Boolean = true, block: suspend () -> Unit) {
        if (action && _pendingActions.value > 0) return
        _pendingWrites.value++
        if (action) _pendingActions.value++
        if (commands.trySend(Command(action, block)).isFailure) {
            _pendingWrites.value--
            if (action) _pendingActions.value--
        }
    }

    fun beginEditor(routine: RoutineWithExercises?) {
        val draft = routine?.let { row ->
            RoutineDraft(row.routine.id, row.routine.name, row.orderedExercises.map {
                RoutineExerciseDraft(it.id, ExerciseChoice(it.catalogueId, it.name, TrackingMetric.valueOf(it.metric),
                    it.perSide, it.assistance), it.targetSets.toString(), it.targetValue.toString())
            })
        } ?: RoutineDraft()
        setEditor(EditorState(routine?.routine?.id?.let { "edit:$it" } ?: "new", draft))
    }

    fun ensureEditor(key: String, routines: List<RoutineWithExercises>) {
        val normalized = if (key == "new" || key.startsWith("edit:")) key else "edit:$key"
        if (_editor.value?.key == normalized) return
        beginEditor(if (normalized == "new") null else routines.find { it.routine.id == normalized.removePrefix("edit:") })
    }

    private fun setEditor(state: EditorState?) {
        _editor.value = state
        savedState["editor"] = state?.toBundle()
    }

    fun updateRoutineName(name: String) = edit { it.copy(name = name) }
    fun addRoutineExercise(choice: ExerciseChoice) = edit { it.copy(exercises = it.exercises + RoutineExerciseDraft(choice = choice)) }
    fun removeRoutineExercise(id: String) = edit { it.copy(exercises = it.exercises.filterNot { row -> row.id == id }) }
    fun updateTargets(id: String, sets: String, value: String) = edit {
        it.copy(exercises = it.exercises.map { row -> if (row.id == id) row.copy(targetSets = sets, targetValue = value) else row })
    }
    fun moveExercise(id: String, delta: Int) = edit { draft ->
        val items = draft.exercises.toMutableList()
        val from = items.indexOfFirst { it.id == id }
        val to = from + delta
        if (from in items.indices && to in items.indices) items.add(to, items.removeAt(from))
        draft.copy(exercises = items)
    }

    private fun edit(change: (RoutineDraft) -> RoutineDraft) {
        _editor.value?.let { setEditor(it.copy(draft = change(it.draft), dirty = true)) }
    }

    fun discardEditor() = setEditor(null)
    fun saveRoutine(onSaved: () -> Unit) {
        val draft = _editor.value?.draft ?: return
        enqueue { repository.saveRoutine(draft); setEditor(null); onSaved() }
    }
    fun deleteRoutine(id: String) = enqueue { repository.deleteRoutine(id) }
    fun startWorkout(routineId: String? = null, onStarted: () -> Unit) = enqueue {
        repository.startWorkout(routineId); onStarted()
    }
    fun addSessionExercise(id: String, choice: ExerciseChoice, onAdded: () -> Unit) = enqueue {
        repository.addExercise(id, choice); onAdded()
    }
    fun saveDraft(id: String, input: SetInput) = enqueue(action = false) { repository.saveDraft(id, input) }
    fun logSet(id: String, input: SetInput) = enqueue { repository.logSet(id, input) }
    fun editSet(id: String, input: SetInput, onSaved: () -> Unit) = enqueue { repository.editSet(id, input); onSaved() }
    fun deleteSet(id: String, onDeleted: () -> Unit) = enqueue { repository.deleteSet(id); onDeleted() }
    fun finishWorkout(id: String, onFinished: () -> Unit) = enqueue { repository.finishWorkout(id); onFinished() }
    fun discardWorkout(id: String, onDiscarded: () -> Unit) = enqueue { repository.discardWorkout(id); onDiscarded() }
    fun session(id: String) = repository.session(id)

    override fun onCleared() { commands.close(); super.onCleared() }

    companion object {
        fun factory(repository: WorkoutRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { WorkoutViewModel(repository, createSavedStateHandle()) }
        }
    }
}

private fun EditorState.toBundle() = Bundle().apply {
    putString("key", key); putString("id", draft.id); putString("name", draft.name); putBoolean("dirty", dirty)
    putParcelableArrayList("exercises", ArrayList(draft.exercises.map { row -> Bundle().apply {
        putString("id", row.id); putString("catalogueId", row.choice.catalogueId); putString("name", row.choice.name)
        putString("metric", row.choice.metric.name); putBoolean("perSide", row.choice.perSide)
        putBoolean("assistance", row.choice.assistance); putString("sets", row.targetSets); putString("value", row.targetValue)
    } }))
}

@Suppress("DEPRECATION")
private fun Bundle.toEditor(): EditorState = EditorState(
    getString("key")!!,
    RoutineDraft(getString("id")!!, getString("name").orEmpty(),
        getParcelableArrayList<Bundle>("exercises").orEmpty().map { row ->
            RoutineExerciseDraft(row.getString("id")!!,
                ExerciseChoice(row.getString("catalogueId"), row.getString("name")!!,
                    TrackingMetric.valueOf(row.getString("metric")!!), row.getBoolean("perSide"), row.getBoolean("assistance")),
                row.getString("sets")!!, row.getString("value")!!)
        }),
    getBoolean("dirty"),
)
