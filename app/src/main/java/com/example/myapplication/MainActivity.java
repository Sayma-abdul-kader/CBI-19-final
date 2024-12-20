package com.example.myapplication;

import android.app.Application;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.room.ColumnInfo;
import androidx.room.Dao;
import androidx.room.Database;
import androidx.room.Entity;
import androidx.room.Insert;
import androidx.room.PrimaryKey;
import androidx.room.Query;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverter;
import androidx.room.TypeConverters;

import com.google.firebase.auth.FirebaseAuth;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private NotesViewModel notesViewModel;
    private ImageView noteImageView;
    private Bitmap noteImageBitmap;

    private final ActivityResultLauncher<Intent> captureImageLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    Bundle extras = result.getData().getExtras();
                    noteImageBitmap = (Bitmap) extras.get("data");
                    noteImageView.setImageBitmap(noteImageBitmap);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        FirebaseAuth mAuth = FirebaseAuth.getInstance();

        // Redirect to LoginActivity if the user is not logged in
        if (mAuth.getCurrentUser() == null) {
            Intent intent = new Intent(this, LoginActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        // Notes setup
        RecyclerView recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        NotesAdapter adapter = new NotesAdapter(new ArrayList<>());
        recyclerView.setAdapter(adapter);

        notesViewModel = new ViewModelProvider(this).get(NotesViewModel.class);
        notesViewModel.getAllNotes().observe(this, adapter::updateNotes);

        // Image integration
        noteImageView = findViewById(R.id.note_image);

        findViewById(R.id.capture_image_button).setOnClickListener(view -> {
            Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            captureImageLauncher.launch(takePictureIntent);
        });

        findViewById(R.id.save_note_button).setOnClickListener(view -> {
            EditText noteEditText = findViewById(R.id.note_edit_text);
            String noteText = noteEditText.getText().toString();
            byte[] noteImage = noteImageBitmap != null ? Converters.fromBitmap(noteImageBitmap) : null;

            if (!noteText.isEmpty()) {
                Note note = new Note(noteText, noteImage);
                notesViewModel.insert(note);
                noteEditText.setText("");
                noteImageView.setImageResource(0);
                noteImageBitmap = null;
                Toast.makeText(this, "Note saved!", Toast.LENGTH_SHORT).show();
            }
        });

        findViewById(R.id.logout_button).setOnClickListener(view -> {
            FirebaseAuth.getInstance().signOut();
            Intent intent = new Intent(this, LoginActivity.class);
            startActivity(intent);
            finish();
        });
    }

    @Entity
    public static class Note {
        @PrimaryKey(autoGenerate = true)
        public int id;

        public String text;

        @ColumnInfo(typeAffinity = ColumnInfo.BLOB)
        public byte[] image;

        public Note(String text, byte[] image) {
            this.text = text;
            this.image = image;
        }
    }

    public static class Converters {
        @TypeConverter
        public static byte[] fromBitmap(Bitmap bitmap) {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
            return outputStream.toByteArray();
        }

        @TypeConverter
        public static Bitmap toBitmap(byte[] bytes) {
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        }
    }

    @Database(entities = {Note.class}, version = 1)
    @TypeConverters({Converters.class})
    public abstract static class NotesDatabase extends RoomDatabase {
        public abstract NoteDao noteDao();
    }

    @Dao
    public interface NoteDao {
        @Insert
        void insert(Note note);

        @Query("SELECT * FROM Note")
        LiveData<List<Note>> getAllNotes();
    }

    public static class NotesViewModel extends AndroidViewModel {
        private final NoteDao noteDao;
        private final LiveData<List<Note>> allNotes;

        public NotesViewModel(Application application) {
            super(application);
            NotesDatabase db = Room.databaseBuilder(application, NotesDatabase.class, "notes-db").build();
            noteDao = db.noteDao();
            allNotes = noteDao.getAllNotes();
        }

        LiveData<List<Note>> getAllNotes() {
            return allNotes;
        }

        void insert(Note note) {
            new Thread(() -> noteDao.insert(note)).start();
        }
    }

    public static class NotesAdapter extends RecyclerView.Adapter<NotesAdapter.NoteViewHolder> {

        private List<Note> notes;

        public NotesAdapter(List<Note> notes) {
            this.notes = notes;
        }

        public void updateNotes(List<Note> newNotes) {
            this.notes = newNotes;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public NoteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.note_item, parent, false);
            return new NoteViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull NoteViewHolder holder, int position) {
            Note note = notes.get(position);
            holder.noteTextView.setText(note.text);
            if (note.image != null) {
                holder.noteImageView.setImageBitmap(Converters.toBitmap(note.image));
            } else {
                holder.noteImageView.setImageResource(R.drawable.placeholder_image);
            }
        }

        @Override
        public int getItemCount() {
            return notes.size();
        }

        static class NoteViewHolder extends RecyclerView.ViewHolder {

            TextView noteTextView;
            ImageView noteImageView;

            public NoteViewHolder(@NonNull View itemView) {
                super(itemView);
                noteTextView = itemView.findViewById(R.id.note_text);
                noteImageView = itemView.findViewById(R.id.note_image);
            }
        }
    }
}
