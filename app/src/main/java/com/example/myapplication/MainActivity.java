package com.example.myapplication;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
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
import android.Manifest;

import com.google.firebase.auth.FirebaseAuth;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private NotesViewModel notesViewModel;
    private ImageView noteImageView;
    private Bitmap noteImageBitmap;

    // Sensor-related variables
    private SensorManager sensorManager;
    private Sensor lightSensor;
    private TextView lightSensorTextView;
    private static final int CAMERA_REQUEST_CODE = 102;
    private Uri imageUri;
    private static final int CAMERA_PERMISSION_CODE = 101;

    // Corrected ActivityResultLauncher for camera handling
    private final ActivityResultLauncher<Intent> captureImageLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    if (imageUri != null) {
                        // Load the full-resolution image from the URI
                        noteImageBitmap = BitmapFactory.decodeFile(imageUri.getPath());
                        noteImageView.setImageBitmap(noteImageBitmap);
                    } else {
                        Toast.makeText(this, "Image URI is null", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(this, "Image capture failed or canceled", Toast.LENGTH_SHORT).show();
                }
            });


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        FirebaseAuth mAuth = FirebaseAuth.getInstance();

        // Redirect to LoginActivity if the user is not logged in
        if (mAuth.getCurrentUser() == null) {
            Intent intent = new Intent(this, login.class);
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
            Intent intent = new Intent(this, login.class);
            startActivity(intent);
            finish();
        });

        Button mapButton = findViewById(R.id.mapButton);
        mapButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, MapActivity.class);
            startActivity(intent);
        });

        // Initialize SensorManager and light sensor
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        lightSensorTextView = findViewById(R.id.light_sensor_text);

        // Check if the device has a light sensor
        if (lightSensor == null) {
            lightSensorTextView.setText("No Light Sensor Available");
        }
    }




    private void requestCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
        } else {
            openCamera();
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                Toast.makeText(this, "Camera permission is required to use this feature.", Toast.LENGTH_SHORT).show();
            }
        }
    }




    private void openCamera() {
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (cameraIntent.resolveActivity(getPackageManager()) != null) {
            File photoFile = createImageFile(); // Create a file to save the image
            if (photoFile != null) {
                imageUri = FileProvider.getUriForFile(this, "com.example.myapplication.fileprovider", photoFile);
                cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
                startActivityForResult(cameraIntent, CAMERA_REQUEST_CODE);
            }
        }
    }

    private File createImageFile() {
        try {
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String imageFileName = "JPEG_" + timestamp + "_";
            File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            return File.createTempFile(imageFileName, ".jpg", storageDir);
        } catch (IOException ex) {
            ex.printStackTrace();
            Toast.makeText(this, "Error creating file", Toast.LENGTH_SHORT).show();
            return null;
        }
    }


    @Override
    protected void onResume() {
        super.onResume();
        // Register the light sensor listener
        if (lightSensor != null) {
            sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Unregister the light sensor listener to save battery
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_LIGHT) {
            float lightLevel = event.values[0];
            lightSensorTextView.setText("Ambient Light Level: " + lightLevel + " lx");
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Handle accuracy changes if needed
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
