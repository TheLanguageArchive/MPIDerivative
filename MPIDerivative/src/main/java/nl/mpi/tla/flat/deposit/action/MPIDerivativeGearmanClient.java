/* 
 * Copyright (C) 2015-2017 The Language Archive
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package nl.mpi.tla.flat.deposit.action;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.XdmDestination;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XsltTransformer;
import nl.mpi.tla.flat.deposit.Context;
import nl.mpi.tla.flat.deposit.DepositException;
import nl.mpi.tla.flat.deposit.sip.Resource;
import nl.mpi.tla.flat.deposit.sip.SIPInterface;
import nl.mpi.tla.flat.deposit.util.Saxon;
import nl.mpi.tla.flat.deposit.util.SaxonListener;

/**
 *
 * @author pavi
 */
public class MPIDerivativeGearmanClient extends AbstractAction {

	boolean res = true;

	private static final Logger logger = LoggerFactory.getLogger(MPIDerivativeGearmanClient.class.getName());
	@Override
	public boolean perform(Context context) throws DepositException {
		try {
			SIPInterface sipInterface = context.getSIP();
			String derivativeConfigParam = this.getParameter("derivative-config");

			File dir = new File(getParameter("fox", "./derivatives-fox"));
			if (!dir.exists()) {
				FileUtils.forceMkdir(dir);
				logger.debug("derivatives-fox dir created: " + dir);
			}
			
			String sip = this.getParameter("sipValue");

			// Check the derivative-config file
			File derivativeConfig = new File(derivativeConfigParam);
			if (!derivativeConfig.exists()) {
				logger.error("The Derivative configuration[" + derivativeConfigParam + "] doesn't exist!");
				return false;
			} else if (!derivativeConfig.isFile()) {
				logger.error("The Derivative configuration[" + derivativeConfigParam + "] isn't a file!");
				return false;
			} else if (!derivativeConfig.canRead()) {
				logger.error("The Derivative configuration[" + derivativeConfigParam + "] can't be read!");
				return false;
			}
			logger.debug("Fedora configuration[" + derivativeConfig.getAbsolutePath() + "]");

			XdmNode nDerivative = Saxon.buildDocument(new StreamSource(derivativeConfig));
			XdmNode derivative = (XdmNode) Saxon.xpathSingle(nDerivative, "/derivative");
			String outputDirName;
			if (derivative != null) {
				outputDirName = Saxon.xpath2string(derivative, "./outputDirName");
				logger.debug("Destination Path for the derivatives generated (derivative-config.xml): " + outputDirName);
			} else {
				throw new DepositException("FAILED! No output path found in file derivative-config.xml");
			}

			XdmItem filetype;

			Set<Resource> resources = sipInterface.getResources();
			
			XsltTransformer dsFile = Saxon.buildTransformer(FOXUpdate.class.getResource("/MPIDerivative/dsFile.xsl")).load();
			SaxonListener listener = new SaxonListener("MPIDerivative", MDC.get("sip"));
			dsFile.setMessageListener(listener);
			dsFile.setErrorListener(listener);
			dsFile.setInitialTemplate(new QName("main"));
			
			for (Resource currentResource : resources) {
				if (currentResource.hasFile()) {
					File currentFile = currentResource.getFile();
					String inputPath = currentFile.getAbsolutePath();

					File outputPath = new File(currentFile.getParentFile()+ "/" + outputDirName);
					if (!outputPath.exists()) {
						try {
							FileUtils.forceMkdir(outputPath);
							logger.info("OutputPath created: " + outputPath);
							// set writable permissions
							outputPath.setWritable(true, false);

						} catch (Exception ex) {
							throw new DepositException(ex);
						}
					}

					// a temp dir to generate the derivatives, then move them into the outputPath given in config file
					File tempDir = new File(currentFile.getParentFile() + "/" + "tmp");
					logger.debug("tempDir: " + tempDir);
					if (!tempDir.exists()) {
						try {
							FileUtils.forceMkdir(tempDir);
							logger.info("tempDir created: " + tempDir);
							// set writable permissions
							tempDir.setWritable(true, false);

						} catch (Exception ex) {
							throw new DepositException(ex);
						}
					}

					String mime = currentResource.getMime().toString();
					String mtpe = mime.replaceAll("/.*", "");

					filetype = Saxon.xpathSingle(derivative, "./filetype/" + mtpe);
					String outputExtn;
					String dsid;
					String mimetype;
					String line;
					String fid1 = currentResource.getFID(true).toString();
					if (filetype!=null) {
						outputExtn = Saxon.xpath2string(filetype, "./outputExtn");
						dsid = Saxon.xpath2string(filetype, "./dsId");
						mimetype = Saxon.xpath2string(filetype, "./mimetype");
						logger.debug("Value of outputExtn from config file: " + outputExtn);
						logger.debug("Value of dsId from config file: " + dsid);
						logger.debug("Value of mimetype from config file: " + mimetype);
						
						//make the foxml for new derivative generated by Gearman to be updated in Fedora
						dsFile.clearParameters();
						
						dsFile.setParameter(new QName("file"), new XdmAtomicValue(outputPath.toString()+ "/" + currentFile.getName()));
						dsFile.setParameter(new QName("mime"), new XdmAtomicValue(mimetype));
						XdmDestination destination = new XdmDestination();
						dsFile.setDestination(destination);
						
						dsFile.transform();
						File fileDestination =  new File (dir + "/" + fid1.replaceAll("[^a-zA-Z0-9]", "_") + "." + dsid + ".file");
						Saxon.save(destination, fileDestination);
						
						//create xml document with data required for job
						DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
						DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

						// root element
						Document doc = docBuilder.newDocument();
						Element rootElement = doc.createElement("job");
						doc.appendChild(rootElement);
						//elements
						Element dsfile = doc.createElement("dsfile");
						dsfile.appendChild(doc.createTextNode(fileDestination.getAbsolutePath()));
						rootElement.appendChild(dsfile);
						Element input = doc.createElement("input");
						input.appendChild(doc.createTextNode(inputPath.toString()));
						rootElement.appendChild(input);
						Element output = doc.createElement("output");
						output.appendChild(doc.createTextNode(tempDir.getAbsolutePath() + "/" + FilenameUtils.removeExtension(currentFile.getName())+"."+outputExtn));
						rootElement.appendChild(output);
						Element outputDirPath = doc.createElement("outputDirPath");
						outputDirPath.appendChild(doc.createTextNode(outputPath.toString()));
						rootElement.appendChild(outputDirPath);
						// write the content into xml file
						TransformerFactory transformerFactory = TransformerFactory.newInstance();
						Transformer transformer = transformerFactory.newTransformer();
						DOMSource source = new DOMSource(doc);
						File fileResult = new File(dir + "/" + fid1.replaceAll("[^a-zA-Z0-9]", "_") + ".job");
						StreamResult result = new StreamResult(fileResult);
						transformer.transform(source, result);
						
						String command = "nohup gearman -b -f ffconvert "+ fileResult.getAbsolutePath() + " " + sip;
						logger.debug("Command being passed: " + command);
						ProcessBuilder pb = new ProcessBuilder("/bin/bash", "-c", command);
						pb.directory(new File(tempDir.getAbsolutePath()));
						pb.redirectErrorStream(true);
						
						Process process = pb.start();
						logger.info("Job submitted");
					}
				}
			}

		} catch (Exception e) {
			throw new DepositException("Connecting to Fedora Commons failed!", e);
		}
		return true;
	}
	
	

}
