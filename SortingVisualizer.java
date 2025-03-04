import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sound.midi.*;

public class SortingVisualizer extends JPanel {
    private int[] array;
    private int barWidth;
    private int delay = 100;
    private String algorithm = "Merge Sort";
    private int current = -1, next = -1;
    private AtomicBoolean isSorting = new AtomicBoolean(false);
    private Thread sortingThread;
    private Synthesizer synthesizer;
    private MidiChannel[] channels;
    private int velocity = 10; 

    private JComboBox<String> algorithmComboBox;
    private JTextField sizeField;
    private JTextField maxValueField;
    private JTextField delayField;
    private JButton generateButton;
    private JButton startButton;
    private JButton stopButton;
    private final Color DARK_GREEN = new Color(0, 100, 0);

    public SortingVisualizer() {
        generateArray(100, 100);
        setLayout(new BorderLayout());

        try {
            synthesizer = MidiSystem.getSynthesizer();
            synthesizer.open();
            channels = synthesizer.getChannels();
            channels[0].programChange(2);
        } catch (MidiUnavailableException e) {
            System.err.println("MIDI синтезатор недоступен");
        }
        
        JPanel controlPanel = new JPanel(new GridLayout(2, 1));
        JPanel topPanel = new JPanel();
        JPanel bottomPanel = new JPanel();

        algorithmComboBox = new JComboBox<>(new String[]{
            "Merge Sort", "Quick Sort", "Heap Sort", "Radix Sort"
        });
        algorithmComboBox.addActionListener(e -> {
            algorithm = (String) algorithmComboBox.getSelectedItem();
        });
    
        sizeField = new JTextField("1000", 5);
        maxValueField = new JTextField("1000", 5);
        delayField = new JTextField("1", 5);

        generateButton = new JButton("Generate New Array");
        generateButton.addActionListener(e -> generateNewArray());

        startButton = new JButton("Sort");
        startButton.addActionListener(e -> sort());

        stopButton = new JButton("Stop");
        stopButton.addActionListener(e -> stopSorting());
        stopButton.setEnabled(false);

        topPanel.add(new JLabel("Algorithm:"));
        topPanel.add(algorithmComboBox);
        topPanel.add(new JLabel("Size:"));
        topPanel.add(sizeField);
        topPanel.add(new JLabel("Max Value:"));
        topPanel.add(maxValueField);
        topPanel.add(new JLabel("Delay (ms):"));
        topPanel.add(delayField);
        
        bottomPanel.add(generateButton);
        bottomPanel.add(startButton);
        bottomPanel.add(stopButton);

        controlPanel.add(topPanel);
        controlPanel.add(bottomPanel);
        add(controlPanel, BorderLayout.SOUTH);
    }

    private void stopSorting() {
        stopNote();
        if (sortingThread != null && sortingThread.isAlive()) {
            isSorting.set(false);
            sortingThread.interrupt();
            stopButton.setEnabled(false);
            startButton.setEnabled(true);
            generateButton.setEnabled(true);
            current = -1;
            next = -1;
            repaint();
        }
    }


    private void generateArray(int size, int maxElement) {
        if (size <= 0 || maxElement <= 0) {
            JOptionPane.showMessageDialog(this, "Invalid parameters! Size and max value must be positive numbers.");
            return;
        }
        
        Random rand = new Random();
        array = new int[size];
        for (int i = 0; i < size; i++) {
            array[i] = rand.nextInt(maxElement) + 1;
        }
        barWidth = Math.max(1, getWidth() / size);
        repaint();
    }

    private void generateNewArray() {
        try {
            int size = Integer.parseInt(sizeField.getText());
            int maxValue = Integer.parseInt(maxValueField.getText());
            delay = Integer.parseInt(delayField.getText());
            generateArray(size, maxValue);
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Invalid input! Please enter numbers only.");
        }
    }

    private void playNote(int value, int maxValue) {
        if (synthesizer == null || !synthesizer.isOpen()) return;
        
        int minFreq = 50;
        int maxFreq = 2000;
        int freq = minFreq + (value * (maxFreq - minFreq)) / maxValue;
        
        int note = (int) (12 * (Math.log(freq / 440.0) / Math.log(2)) + 69);
        note = Math.min(127, Math.max(0, note));
        
        channels[0].noteOn(note, velocity);
    }

    private void stopNote() {
        if (synthesizer != null && synthesizer.isOpen()) {
            channels[0].allNotesOff();
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (array == null) return;

        int maxValue = Arrays.stream(array).max().getAsInt();
        
        for (int i = 0; i < array.length; i++) {
            int height = (int)((array[i] / (double)Arrays.stream(array).max().getAsInt()) * (getHeight() - 50));
            g.setColor((i == current || i == next) ? Color.RED : DARK_GREEN);
            g.fillRect(i * barWidth, getHeight() - height, barWidth, height);

            if (i == current || i == next) {
                playNote(array[i], maxValue);
            }
        }
    }

    private void sort() {
        if (isSorting.get()) return;
        
        isSorting.set(true);
        startButton.setEnabled(false);
        generateButton.setEnabled(false);
        stopButton.setEnabled(true);
        
        sortingThread = new Thread(() -> {
            try {
                current = -1;
                next = -1;
                
                switch (algorithm) {
                    case "Merge Sort":
                        mergeSort(0, array.length - 1);
                        break;
                    case "Quick Sort":
                        quickSort(0, array.length - 1);
                        break;
                    case "Heap Sort":
                        heapSort();
                        break;
                    case "Radix Sort":
                        radixSort();
                        break;
                }
                if (!Thread.currentThread().isInterrupted()) {
                    finishAnimation();
                }
            } finally {
                isSorting.set(false);
                SwingUtilities.invokeLater(() -> {
                    startButton.setEnabled(true);
                    generateButton.setEnabled(true);
                    stopButton.setEnabled(false);
                });
            }
        });
        sortingThread.start();
    }

    private void finishAnimation() {
        for (int i = 0; i < array.length && !Thread.currentThread().isInterrupted(); i++) {
            current = i;
            next = -1;
            repaint();
            sleep();
        }
        current = -1;
        next = -1;
        repaint();
    }


    private void mergeSort(int low, int high) {
        if (low < high && isSorting.get() && !Thread.currentThread().isInterrupted()) {
            int mid = (low + high) / 2;
            mergeSort(low, mid);
            mergeSort(mid + 1, high);
            merge(low, mid, high);
        }
    }
    
    private void merge(int left, int mid, int right) {
        int[] temp = new int[right - left + 1];
        int i = left, j = mid + 1, k = 0;
    
        while (i <= mid && j <= right && !Thread.currentThread().isInterrupted()) {
            current = i;
            next = j;
            if (array[i] <= array[j]) temp[k++] = array[i++];
            else temp[k++] = array[j++];
            repaint();
            sleep();
        }
    
        while (i <= mid && !Thread.currentThread().isInterrupted()) {
            current = i;
            temp[k++] = array[i++];
            repaint();
            sleep();
        }
    
        while (j <= right && !Thread.currentThread().isInterrupted()) {
            next = j;
            temp[k++] = array[j++];
            repaint();
            sleep();
        }
    
        if (!Thread.currentThread().isInterrupted()) {
            System.arraycopy(temp, 0, array, left, temp.length);
            repaint();
        }
        repaint();
    }
    
    private void quickSort(int low, int high) {
        if (low < high && isSorting.get() && !Thread.currentThread().isInterrupted()) {
            int pi = partition(low, high);
            quickSort(low, pi - 1);
            quickSort(pi + 1, high);
        }
    }
    
    private int partition(int low, int high) {
        int pivot = array[high];
        int i = low - 1;
        for (int j = low; j < high && !Thread.currentThread().isInterrupted(); j++) {
            current = j;
            if (array[j] < pivot) {
                i++;
                swap(i, j);
                repaint();
                sleep();
            }
        }
        if (!Thread.currentThread().isInterrupted()) {
            swap(i + 1, high);
            repaint();
            sleep();
        }
        repaint();
        return i + 1;
    }
    
    private void heapSort() {
        int n = array.length;
    
        for (int i = n / 2 - 1; i >= 0 && !Thread.currentThread().isInterrupted(); i--) {
            heapify(n, i);
        }
    
        for (int i = n - 1; i > 0 && !Thread.currentThread().isInterrupted(); i--) {
            current = 0;
            next = i;
            sleep();
            swap(0, i);
            heapify(i, 0);
        }
        current = -1;
        next = -1;
    }
    
    private void heapify(int n, int i) {
        if (Thread.currentThread().isInterrupted()) return;
        
        int largest = i;
        int l = 2 * i + 1;
        int r = 2 * i + 2;
    
        if (l < n && !Thread.currentThread().isInterrupted()) {
            current = l;
            next = largest;
            sleep();
            if (array[l] > array[largest]) largest = l;
        }
    
        if (r < n && !Thread.currentThread().isInterrupted()) {
            current = r;
            next = largest;
            sleep();
            if (array[r] > array[largest]) largest = r;
        }
    
        if (largest != i && !Thread.currentThread().isInterrupted()) {
            current = i;
            next = largest;
            sleep();
            swap(i, largest);
            heapify(n, largest);
        }
        current = -1;
        next = -1;
        repaint();
    }
    
    private void radixSort() {
        int max = Arrays.stream(array).max().orElse(1);
        for (int exp = 1; max / exp > 0 && !Thread.currentThread().isInterrupted(); exp *= 10) {
            countSort(exp);
            repaint();
            sleep();
        }
    }
    
    private void countSort(int exp) {
        int[] output = new int[array.length];
        int[] count = new int[10];
        Arrays.fill(count, 0);
    
        for (int j : array) count[(j / exp) % 10]++;
    
        for (int i = 1; i < 10; i++) count[i] += count[i - 1];
    
        for (int i = array.length - 1; i >= 0 && !Thread.currentThread().isInterrupted(); i--) {
            int index = (array[i] / exp) % 10;
            output[count[index] - 1] = array[i];
            count[index]--;
        }
    
        for (int i = 0; i < array.length && !Thread.currentThread().isInterrupted(); i++) {
            current = i;
            next = -1;
            array[i] = output[i];
            repaint();
            sleep();
        }
        current = -1;
        next = -1;
        repaint();
    }

    private void swap(int i, int j) {
        int temp = array[i];
        array[i] = array[j];
        array[j] = temp;
        repaint();
    }

    private void sleep() {
        try {
            Thread.sleep(delay);
            stopNote();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Sorting Visualizer");
            SortingVisualizer panel = new SortingVisualizer();
            
            frame.add(panel);
            frame.setSize(800, 600);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setVisible(true);
        });
    }
}
