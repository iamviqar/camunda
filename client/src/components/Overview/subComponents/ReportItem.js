import React from 'react';
import {Button, Icon} from 'components';
import {getReportInfo, getReportIcon} from '../service';
import LastModified from './LastModified';
import {Link} from 'react-router-dom';

export default function ReportItem({
  report,
  duplicateEntity,
  showDeleteModalFor,
  collection,
  renderCollectionsDropdown
}) {
  const {Icon: ReportIcon, label} = getReportIcon(report);

  return (
    <li className="ReportItem listItem">
      <Link className="info" to={`/report/${report.id}`}>
        <span className="icon" title={label}>
          <ReportIcon />
        </span>
        <div className="textInfo">
          <div className="data dataTitle">
            <h3>{report.name}</h3>
            {report.combined && <span>Combined</span>}
            {report.reportType && report.reportType === 'decision' && <span>Decision</span>}
          </div>
          <div className="extraInfo">
            <span className="data custom">{getReportInfo(report)}</span>
            <LastModified date={report.lastModified} author={report.lastModifier} />
          </div>
        </div>
      </Link>
      {renderCollectionsDropdown(report, collection)}
      <div className="operations">
        <Link title="Edit Report" to={`/report/${report.id}/edit`}>
          <Icon title="Edit Report" type="edit" className="editLink" />
        </Link>
        <Button title="Duplicate Report" onClick={duplicateEntity('report', report, collection)}>
          <Icon type="copy-document" title="Duplicate Report" className="duplicateIcon" />
        </Button>
        <Button
          title="Delete Report"
          onClick={showDeleteModalFor({type: 'report', entity: report})}
        >
          <Icon type="delete" title="Delete Report" className="deleteIcon" />
        </Button>
      </div>
    </li>
  );
}
