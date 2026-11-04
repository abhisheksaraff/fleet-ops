package com.fleetops.gui;

import com.fleetops.domain.incident.Incident;
import java.util.ArrayList;
import java.util.List;
import javax.swing.table.AbstractTableModel;

/** Table model listing incidents; rows are replaced as a whole on every refresh. */
final class IncidentTableModel extends AbstractTableModel {

    private static final long serialVersionUID = 1L;
    private static final String[] COLUMNS = {"ID", "Type", "State", "Severity", "People", "Title"};

    private transient List<Incident> rows = new ArrayList<>();

    void setIncidents(List<Incident> incidents) {
        rows = new ArrayList<>(incidents);
        fireTableDataChanged();
    }

    Incident incidentAt(int row) {
        return rows.get(row);
    }

    int rowOf(Incident incident) {
        return rows.indexOf(incident);
    }

    List<Incident> incidents() {
        return List.copyOf(rows);
    }

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMNS[column];
    }

    @Override
    public Class<?> getColumnClass(int column) {
        return column == 0 || column == 4 ? Integer.class : String.class;
    }

    @Override
    public Object getValueAt(int row, int column) {
        Incident i = rows.get(row);
        return switch (column) {
            case 0 -> i.id().map(Long::intValue).orElse(0);
            case 1 -> i.type().keyword();
            case 2 -> i.state().toString();
            case 3 -> i.severity().toString();
            case 4 -> i.estimatedPeople();
            default -> i.title();
        };
    }
}
