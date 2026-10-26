package dev.laranja.jobhunter.network;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;

import dev.laranja.jobhunter.shared.jdbc.JdbcRows;

/** Leitura das linhas de contato e de conversa, compartilhada pelas consultas do módulo. */
final class ContactRows {

	static final String INTERACTION_SELECT = """
			select i.id, i.contact_id, c.name as contact_name, c.role as contact_role, i.application_id, i.channel,
			       i.direction, i.kind, i.summary, i.occurred_at
			from contact_interaction i
			join contact c on c.id = i.contact_id
			""";

	private ContactRows() {
	}

	static ContactView view(ResultSet rs) throws SQLException {
		Date connectedOn = rs.getDate("connected_on");
		return new ContactView(rs.getLong("id"), rs.getString("name"), ContactRole.valueOf(rs.getString("role")),
				JdbcRows.nullableLong(rs, "company_id"), rs.getString("company_name"), rs.getString("position"),
				rs.getString("linkedin_url"), rs.getString("email"), rs.getString("notes"),
				ContactSource.valueOf(rs.getString("source")), (connectedOn != null) ? connectedOn.toLocalDate() : null,
				JdbcRows.instant(rs, "last_interaction_at"), JdbcRows.instant(rs, "created_at"));
	}

	static ContactInteractionView interaction(ResultSet rs) throws SQLException {
		return new ContactInteractionView(rs.getLong("id"), rs.getLong("contact_id"), rs.getString("contact_name"),
				ContactRole.valueOf(rs.getString("contact_role")), JdbcRows.nullableLong(rs, "application_id"),
				InteractionChannel.valueOf(rs.getString("channel")),
				InteractionDirection.valueOf(rs.getString("direction")), InteractionKind.valueOf(rs.getString("kind")),
				rs.getString("summary"), JdbcRows.instant(rs, "occurred_at"));
	}

}
